/*
 * Copyright (c) 2026 Robert Bosch Manufacturing Solutions GmbH
 *
 * See the AUTHORS file(s) distributed with this work for
 * additional information regarding authorship.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.eclipse.esmf.ame.services.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.esmf.ame.exceptions.GithubSourceException;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceConfig;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceMetadata;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceStatus;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks and synchronizes the read-only GitHub sources.
 * <ul>
 *    <li>A <b>check</b> only asks GitHub for the commit the branch or tag currently points to; nothing is downloaded.</li>
 *    <li>A <b>sync</b> downloads the repository archive of that commit, extracts the {@code .ttl} files below the configured
 *    directory and replaces the local copy atomically. It is only ever triggered by the user.</li>
 * </ul>
 * Access tokens are passed per call and never stored or logged. Without a token the environment variable
 * {@code GITHUB_TOKEN} is used, if set. Requests use the proxy from {@code HTTPS_PROXY}/{@code HTTP_PROXY}
 * (honoring {@code NO_PROXY}) or the system proxy settings.
 */
@Singleton
public class GithubSourceService {
   public static final String METADATA_FILE = ".ame-source.json";

   static final long MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;
   static final long MAX_EXTRACTED_BYTES = 1024L * 1024 * 1024;
   static final int MAX_EXTRACTED_FILES = 50_000;

   private static final Logger LOG = LoggerFactory.getLogger( GithubSourceService.class );
   private static final Pattern SHA_PATTERN = Pattern.compile( "[0-9a-f]{40}" );
   private static final String USER_AGENT = "esmf-aspect-model-editor";
   private static final String STAGING_PREFIX = ".staging-";
   private static final String OLD_PREFIX = ".old-";
   private static final int MAX_REDIRECTS = 5;

   private final WorkspaceConfigService workspaceConfigService;
   private final DynamicResolutionStrategy resolutionStrategy;
   private final HttpClient httpClient;
   private final @Nullable URI apiBaseOverride;
   private final @Nullable String environmentToken;
   private final JsonMapper jsonMapper = JsonMapper.createDefault();
   private final Map<String, CheckResult> checkResults = new ConcurrentHashMap<>();
   private final Map<String, Object> locks = new ConcurrentHashMap<>();

   private record CheckResult( @Nullable String remoteSha, String checkedAt, @Nullable String error ) {}

   @Inject
   public GithubSourceService( final WorkspaceConfigService workspaceConfigService,
         final DynamicResolutionStrategy resolutionStrategy ) {
      this( workspaceConfigService, resolutionStrategy, createHttpClient(), null, System.getenv( "GITHUB_TOKEN" ) );
   }

   GithubSourceService( final WorkspaceConfigService workspaceConfigService, final DynamicResolutionStrategy resolutionStrategy,
         final HttpClient httpClient, final @Nullable URI apiBaseOverride, final @Nullable String environmentToken ) {
      this.workspaceConfigService = workspaceConfigService;
      this.resolutionStrategy = resolutionStrategy;
      this.httpClient = httpClient;
      this.apiBaseOverride = apiBaseOverride;
      this.environmentToken = isBlank( environmentToken ) ? null : environmentToken.trim();
   }

   /**
    * Returns all configured GitHub sources with the state of their local copies.
    *
    * @return the source states in configuration order
    */
   public List<GithubSourceStatus> getStatuses() {
      return workspaceConfigService.getConfig().githubSources().stream().map( this::status ).toList();
   }

   /**
    * Returns the state of one GitHub source.
    *
    * @param id the source id
    * @return the state
    */
   public GithubSourceStatus getStatus( final String id ) {
      return status( workspaceConfigService.getGithubSource( id ) );
   }

   /**
    * Forgets the check result of a removed or changed source.
    *
    * @param id the source id
    */
   public void forget( final String id ) {
      checkResults.remove( id );
   }

   /**
    * Asks GitHub for the commit the branch or tag of a source points to. Errors are not thrown but reported in
    * {@link GithubSourceStatus#lastError()}, so a check of several sources can report each result.
    *
    * @param id the source id
    * @param token an optional access token
    * @return the state including the check result
    */
   public GithubSourceStatus check( final String id, final @Nullable String token ) {
      final GithubSourceConfig source = workspaceConfigService.getGithubSource( id );
      try {
         final String sha = fetchCommitSha( source, effectiveToken( token ) );
         checkResults.put( id, new CheckResult( sha, Instant.now().toString(), null ) );
      } catch ( final GithubSourceException e ) {
         LOG.warn( "Checking GitHub source {} failed: {}", id, e.getMessage() );
         final CheckResult previous = checkResults.get( id );
         checkResults.put( id, new CheckResult( previous != null ? previous.remoteSha() : null, Instant.now().toString(),
               e.getMessage() ) );
      }
      return status( source );
   }

   /**
    * Downloads the current commit of a source and replaces its local copy.
    *
    * @param id the source id
    * @param token an optional access token
    * @return the new state
    * @throws GithubSourceException if GitHub cannot be reached, rejects the request or the archive is invalid
    */
   public GithubSourceStatus sync( final String id, final @Nullable String token ) {
      synchronized ( locks.computeIfAbsent( id, key -> new Object() ) ) {
         final GithubSourceConfig source = workspaceConfigService.getGithubSource( id );
         try {
            final String effectiveToken = effectiveToken( token );
            final String sha = fetchCommitSha( source, effectiveToken );
            final Path cache = workspaceConfigService.getGithubCachePath();
            Files.createDirectories( cache );
            cleanupLeftovers( cache, id );

            final Path archive = Files.createTempFile( cache, STAGING_PREFIX + id + "-", ".zip" );
            final Path staging = cache.resolve( STAGING_PREFIX + id + "-" + System.nanoTime() );
            try {
               download( archiveUri( source, sha ), effectiveToken, archive );
               final int files = extract( archive, source.directory(), staging );
               LOG.info( "Extracted {} model files of GitHub source {} at {}", files, id, sha );
               writeMetadata( staging, new GithubSourceMetadata( sha, Instant.now().toString(), source.displayName() ) );
               // The source might have been removed or changed while downloading
               workspaceConfigService.getGithubSource( id );
               replace( staging, workspaceConfigService.getGithubSourceDirectory( source ) );
            } finally {
               Files.deleteIfExists( archive );
               deleteRecursively( staging );
            }
            checkResults.put( id, new CheckResult( sha, Instant.now().toString(), null ) );
            resolutionStrategy.invalidate();
            return status( source );
         } catch ( final GithubSourceException e ) {
            rememberError( id, e.getMessage() );
            throw e;
         } catch ( final IOException | UncheckedIOException e ) {
            final String message = "Synchronizing " + source.displayName() + " failed: " + e.getMessage();
            rememberError( id, message );
            throw new GithubSourceException( message, e );
         }
      }
   }

   private void rememberError( final String id, final String message ) {
      final CheckResult previous = checkResults.get( id );
      checkResults.put( id, new CheckResult( previous != null ? previous.remoteSha() : null, Instant.now().toString(), message ) );
   }

   private GithubSourceStatus status( final GithubSourceConfig source ) {
      final Path directory = workspaceConfigService.getGithubSourceDirectory( source );
      final boolean synced = Files.isDirectory( directory );
      final Optional<GithubSourceMetadata> metadata = synced ? readMetadata( directory ) : Optional.empty();
      final CheckResult check = checkResults.get( source.id() );
      final String syncedSha = metadata.map( GithubSourceMetadata::sha ).orElse( null );
      final String remoteSha = check != null ? check.remoteSha() : null;
      final boolean updateAvailable = remoteSha != null && ( syncedSha == null || !syncedSha.equals( remoteSha ) );
      return new GithubSourceStatus( source, directory.toString(), synced, syncedSha,
            metadata.map( GithubSourceMetadata::syncedAt ).orElse( null ), remoteSha, check != null ? check.checkedAt() : null,
            updateAvailable, check != null ? check.error() : null );
   }

   private @Nullable String effectiveToken( final @Nullable String token ) {
      return isBlank( token ) ? environmentToken : token.trim();
   }

   URI apiBase( final GithubSourceConfig source ) {
      if ( apiBaseOverride != null ) {
         return apiBaseOverride;
      }
      return GithubSourceConfig.DEFAULT_HOST.equals( source.host() )
            ? URI.create( "https://api.github.com" )
            : URI.create( "https://" + source.host() + "/api/v3" );
   }

   private URI repositoryUri( final GithubSourceConfig source, final String path ) {
      final String base = apiBase( source ).toString().replaceAll( "/+$", "" );
      return URI.create( base + "/repos/" + source.owner() + "/" + source.repository() + "/" + path );
   }

   private URI archiveUri( final GithubSourceConfig source, final String sha ) {
      return repositoryUri( source, "zipball/" + sha );
   }

   private String fetchCommitSha( final GithubSourceConfig source, final @Nullable String token ) {
      final HttpRequest request = requestBuilder( repositoryUri( source, "commits/" + source.ref() ), token, true )
            .header( "Accept", "application/vnd.github.sha" )
            .timeout( Duration.ofSeconds( 30 ) )
            .GET()
            .build();
      final HttpResponse<String> response = send( request, HttpResponse.BodyHandlers.ofString(), source );
      if ( response.statusCode() != 200 ) {
         throw new GithubSourceException( describeError( source, response.statusCode(), response.headers() ) );
      }
      final String sha = response.body().trim().toLowerCase( Locale.ROOT );
      if ( !SHA_PATTERN.matcher( sha ).matches() ) {
         throw new GithubSourceException( "GitHub returned an unexpected answer for " + source.displayName() + "." );
      }
      return sha;
   }

   private void download( final URI uri, final @Nullable String token, final Path target ) throws IOException {
      URI current = uri;
      final String apiHost = uri.getHost();
      for ( int redirect = 0; redirect <= MAX_REDIRECTS; redirect++ ) {
         // The token is only sent to the API host, never to the host the archive is redirected to
         final boolean sendToken = apiHost.equalsIgnoreCase( current.getHost() );
         final HttpRequest request = requestBuilder( current, token, sendToken )
               .timeout( Duration.ofMinutes( 10 ) )
               .GET()
               .build();
         final HttpResponse<InputStream> response = send( request, HttpResponse.BodyHandlers.ofInputStream(), null );
         final int code = response.statusCode();
         if ( code >= 300 && code < 400 ) {
            response.body().close();
            final String location = response.headers().firstValue( "Location" )
                  .orElseThrow( () -> new GithubSourceException( "GitHub redirected the download without a target." ) );
            current = current.resolve( location );
            if ( !"https".equalsIgnoreCase( current.getScheme() ) && !"http".equalsIgnoreCase( uri.getScheme() ) ) {
               throw new GithubSourceException( "GitHub redirected the download to an insecure location." );
            }
            continue;
         }
         if ( code != 200 ) {
            response.body().close();
            throw new GithubSourceException( "Downloading the repository archive failed (HTTP " + code + ")." );
         }
         try ( final InputStream in = response.body(); final OutputStream out = Files.newOutputStream( target ) ) {
            copyLimited( in, out, MAX_ARCHIVE_BYTES, "The repository archive is too large." );
         }
         return;
      }
      throw new GithubSourceException( "Too many redirects while downloading the repository archive." );
   }

   private HttpRequest.Builder requestBuilder( final URI uri, final @Nullable String token, final boolean sendToken ) {
      final HttpRequest.Builder builder = HttpRequest.newBuilder( uri )
            .header( "User-Agent", USER_AGENT )
            .header( "X-GitHub-Api-Version", "2022-11-28" );
      if ( sendToken && token != null ) {
         builder.header( "Authorization", "Bearer " + token );
      }
      return builder;
   }

   private <T> HttpResponse<T> send( final HttpRequest request, final HttpResponse.BodyHandler<T> handler,
         final @Nullable GithubSourceConfig source ) {
      try {
         return httpClient.send( request, handler );
      } catch ( final IOException e ) {
         final String host = source != null ? source.host() : request.uri().getHost();
         throw new GithubSourceException( host + " is not reachable (" + e.getClass().getSimpleName()
               + ( e.getMessage() != null ? ": " + e.getMessage() : "" ) + "). Check the network and proxy settings.", e );
      } catch ( final InterruptedException e ) {
         Thread.currentThread().interrupt();
         throw new GithubSourceException( "The request to GitHub was interrupted.", e );
      }
   }

   static String describeError( final GithubSourceConfig source, final int statusCode, final java.net.http.HttpHeaders headers ) {
      final boolean rateLimited = headers.firstValue( "x-ratelimit-remaining" ).map( "0"::equals ).orElse( false );
      if ( statusCode == 429 || ( statusCode == 403 && rateLimited ) ) {
         return "The GitHub rate limit is exceeded. Add an access token or try again later.";
      }
      return switch ( statusCode ) {
         case 401 -> "GitHub rejected the access token. Check or renew the token for " + source.host() + ".";
         case 403 -> "GitHub denied access to " + source.displayName()
               + ". The token might lack read permission for the repository or SSO authorization.";
         case 404, 422 -> "Repository, branch or tag not found: " + source.displayName()
               + ". Private repositories require an access token.";
         case 407 -> "The proxy requires authentication.";
         default -> "GitHub answered with HTTP " + statusCode + " for " + source.displayName() + ".";
      };
   }

   /**
    * Extracts the {@code .ttl} files below {@code directory} of a GitHub repository archive. Archive entries are
    * prefixed with a single top-level directory ({@code <owner>-<repo>-<sha>/}), which is removed.
    *
    * @return the number of extracted files
    */
   static int extract( final Path archive, final String directory, final Path target ) throws IOException {
      final String prefix = directory.isEmpty() ? "" : directory + "/";
      final Path root = target.toAbsolutePath().normalize();
      Files.createDirectories( root );
      boolean directoryFound = prefix.isEmpty();
      int files = 0;
      long bytes = 0;
      try ( final ZipFile zip = new ZipFile( archive.toFile() ) ) {
         final Enumeration<? extends ZipEntry> entries = zip.entries();
         while ( entries.hasMoreElements() ) {
            final ZipEntry entry = entries.nextElement();
            final String name = entry.getName().replace( '\\', '/' );
            final int slash = name.indexOf( '/' );
            if ( slash < 0 ) {
               continue;
            }
            final String inRepository = name.substring( slash + 1 );
            if ( !inRepository.startsWith( prefix ) ) {
               continue;
            }
            directoryFound = true;
            final String relative = inRepository.substring( prefix.length() );
            if ( entry.isDirectory() || relative.isEmpty() || !relative.toLowerCase( Locale.ROOT ).endsWith( ".ttl" ) ) {
               continue;
            }
            final Path file = root.resolve( relative ).normalize();
            if ( !file.startsWith( root ) || file.equals( root ) ) {
               throw new GithubSourceException( "The repository archive contains an invalid path: " + entry.getName() );
            }
            if ( ++files > MAX_EXTRACTED_FILES ) {
               throw new GithubSourceException( "The repository contains too many model files." );
            }
            Files.createDirectories( file.getParent() );
            try ( final InputStream in = zip.getInputStream( entry ); final OutputStream out = Files.newOutputStream( file ) ) {
               bytes += copyLimited( in, out, MAX_EXTRACTED_BYTES - bytes, "The models of the repository are too large." );
            }
         }
      } catch ( final java.util.zip.ZipException e ) {
         throw new GithubSourceException( "The repository archive is invalid: " + e.getMessage(), e );
      }
      if ( !directoryFound ) {
         throw new GithubSourceException( "The directory '" + directory + "' does not exist in the repository." );
      }
      if ( files == 0 ) {
         throw new GithubSourceException( "No Aspect Models (.ttl files) found"
               + ( directory.isEmpty() ? " in the repository." : " in '" + directory + "'." ) );
      }
      return files;
   }

   private static long copyLimited( final InputStream in, final OutputStream out, final long limit, final String message )
         throws IOException {
      final byte[] buffer = new byte[64 * 1024];
      long total = 0;
      int read;
      while ( ( read = in.read( buffer ) ) != -1 ) {
         total += read;
         if ( total > limit ) {
            throw new GithubSourceException( message );
         }
         out.write( buffer, 0, read );
      }
      return total;
   }

   private void writeMetadata( final Path directory, final GithubSourceMetadata metadata ) throws IOException {
      Files.write( directory.resolve( METADATA_FILE ), jsonMapper.writeValueAsBytes( metadata ) );
   }

   private Optional<GithubSourceMetadata> readMetadata( final Path directory ) {
      final Path file = directory.resolve( METADATA_FILE );
      if ( !Files.isRegularFile( file ) ) {
         return Optional.empty();
      }
      try {
         return Optional.ofNullable( jsonMapper.readValue( Files.readAllBytes( file ), GithubSourceMetadata.class ) );
      } catch ( final IOException e ) {
         LOG.warn( "Could not read {}: {}", file, e.getMessage() );
         return Optional.empty();
      }
   }

   private static void replace( final Path staging, final Path target ) throws IOException {
      final Path old = target.resolveSibling( OLD_PREFIX + target.getFileName() + "-" + System.nanoTime() );
      final boolean hadCopy = Files.exists( target );
      if ( hadCopy ) {
         Files.move( target, old, StandardCopyOption.ATOMIC_MOVE );
      }
      try {
         Files.move( staging, target, StandardCopyOption.ATOMIC_MOVE );
      } catch ( final IOException e ) {
         if ( hadCopy ) {
            Files.move( old, target, StandardCopyOption.ATOMIC_MOVE );
         }
         throw e;
      }
      deleteRecursively( old );
   }

   private static void cleanupLeftovers( final Path cache, final String id ) throws IOException {
      try ( final Stream<Path> children = Files.list( cache ) ) {
         for ( final Path child : children.toList() ) {
            final String name = child.getFileName().toString();
            if ( name.startsWith( STAGING_PREFIX + id + "-" ) || name.startsWith( OLD_PREFIX + id + "-" ) ) {
               deleteRecursively( child );
            }
         }
      }
   }

   private static void deleteRecursively( final Path path ) {
      if ( !Files.exists( path ) ) {
         return;
      }
      try ( final Stream<Path> paths = Files.walk( path ) ) {
         for ( final Path p : paths.sorted( Comparator.reverseOrder() ).toList() ) {
            Files.deleteIfExists( p );
         }
      } catch ( final IOException | UncheckedIOException e ) {
         LOG.warn( "Could not delete {}: {}", path, e.getMessage() );
      }
   }

   private static boolean isBlank( final @Nullable String value ) {
      return value == null || value.isBlank();
   }

   private static HttpClient createHttpClient() {
      final HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout( Duration.ofSeconds( 15 ) )
            .followRedirects( HttpClient.Redirect.NEVER );
      final ProxySelector proxy = EnvironmentProxySelector.fromEnvironment().orElseGet( ProxySelector::getDefault );
      if ( proxy != null ) {
         builder.proxy( proxy );
      }
      return builder.build();
   }

   /**
    * Uses the proxy of the {@code HTTPS_PROXY}/{@code HTTP_PROXY} environment variables, except for the hosts listed in
    * {@code NO_PROXY}.
    */
   static final class EnvironmentProxySelector extends ProxySelector {
      private final Proxy proxy;
      private final List<String> noProxy;

      EnvironmentProxySelector( final Proxy proxy, final List<String> noProxy ) {
         this.proxy = proxy;
         this.noProxy = noProxy;
      }

      static Optional<ProxySelector> fromEnvironment() {
         return from( System.getenv() );
      }

      static Optional<ProxySelector> from( final Map<String, String> env ) {
         final String value = Stream.of( "HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy" )
               .map( env::get )
               .filter( v -> !isBlank( v ) )
               .findFirst()
               .orElse( null );
         if ( value == null ) {
            return Optional.empty();
         }
         try {
            final URI uri = URI.create( value.contains( "://" ) ? value.trim() : "http://" + value.trim() );
            if ( uri.getHost() == null ) {
               return Optional.empty();
            }
            final int port = uri.getPort() != -1 ? uri.getPort() : 80;
            final String noProxyValue = Optional.ofNullable( env.get( "NO_PROXY" ) ).orElse( env.getOrDefault( "no_proxy", "" ) );
            final List<String> noProxy = Stream.of( noProxyValue.split( "," ) )
                  .map( String::trim )
                  .filter( host -> !host.isEmpty() )
                  .map( host -> host.toLowerCase( Locale.ROOT ) )
                  .toList();
            return Optional.of( new EnvironmentProxySelector(
                  new Proxy( Proxy.Type.HTTP, InetSocketAddress.createUnresolved( uri.getHost(), port ) ), noProxy ) );
         } catch ( final IllegalArgumentException e ) {
            LOG.warn( "Ignoring invalid proxy setting: {}", e.getMessage() );
            return Optional.empty();
         }
      }

      @Override
      public List<Proxy> select( final URI uri ) {
         final String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase( Locale.ROOT );
         final boolean bypass = noProxy.stream().anyMatch( entry -> "*".equals( entry ) || host.equals( entry )
               || host.endsWith( entry.startsWith( "." ) ? entry : "." + entry ) );
         return List.of( bypass ? Proxy.NO_PROXY : proxy );
      }

      @Override
      public void connectFailed( final URI uri, final SocketAddress address, final IOException e ) {
         LOG.debug( "Proxy connection to {} failed: {}", uri, e.getMessage() );
      }
   }
}
