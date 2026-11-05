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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.esmf.ame.exceptions.GithubSourceException;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceConfig;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceStatus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks and synchronizes GitHub sources against a local fake of the GitHub API.
 */
class GithubSourceServiceTest {
   private static final String SHA_1 = "1111111111111111111111111111111111111111";
   private static final String SHA_2 = "2222222222222222222222222222222222222222";
   private static final String TOKEN = "secret-token";

   @TempDir
   Path tempDir;

   private HttpServer server;
   private volatile String currentSha = SHA_1;
   private volatile int commitStatus = 200;
   private final Map<String, byte[]> archives = new ConcurrentHashMap<>();
   private final Map<String, String> authorizationByPath = new ConcurrentHashMap<>();
   private WorkspaceConfigService configService;
   private GithubSourceService service;
   private String sourceId;

   @BeforeEach
   void setUp() throws IOException {
      server = HttpServer.create( new InetSocketAddress( "127.0.0.1", 0 ), 0 );
      final int port = server.getAddress().getPort();
      server.createContext( "/repos/owner/repo/commits/", exchange -> {
         record( exchange );
         if ( commitStatus != 200 ) {
            respond( exchange, commitStatus, new byte[0] );
            return;
         }
         respond( exchange, 200, currentSha.getBytes( StandardCharsets.UTF_8 ) );
      } );
      server.createContext( "/repos/owner/repo/zipball/", exchange -> {
         record( exchange );
         final String sha = exchange.getRequestURI().getPath().substring( "/repos/owner/repo/zipball/".length() );
         // Redirect to another host name, like GitHub redirects to codeload.github.com
         exchange.getResponseHeaders().add( "Location", "http://localhost:" + port + "/archive/" + sha + ".zip" );
         respond( exchange, 302, new byte[0] );
      } );
      server.createContext( "/archive/", exchange -> {
         record( exchange );
         final String sha = exchange.getRequestURI().getPath().replace( "/archive/", "" ).replace( ".zip", "" );
         final byte[] archive = archives.get( sha );
         respond( exchange, archive != null ? 200 : 404, archive != null ? archive : new byte[0] );
      } );
      server.start();

      configService = new WorkspaceConfigService( tempDir.resolve( "workspaces.json" ), tempDir.resolve( "models" ),
            JsonMapper.createDefault() );
      sourceId = configService.addGithubSource(
            new GithubSourceConfig( null, null, "owner", "repo", null, "main", "core/models", null ) ).githubSources().getLast().id();
      service = new GithubSourceService( configService, new DynamicResolutionStrategy( configService ),
            HttpClient.newBuilder().followRedirects( HttpClient.Redirect.NEVER ).proxy( java.net.ProxySelector.of( null ) ).build(),
            URI.create( "http://127.0.0.1:" + port ), null );

      archives.put( SHA_1, zip( Map.of(
            "owner-repo-1/core/models/org.example/1.0.0/A.ttl", "A1",
            "owner-repo-1/core/models/org.example/1.0.0/B.ttl", "B1",
            "owner-repo-1/core/models/README.md", "readme",
            "owner-repo-1/core/other/C.ttl", "C" ) ) );
      archives.put( SHA_2, zip( Map.of(
            "owner-repo-2/core/models/org.example/1.0.0/A.ttl", "A2" ) ) );
   }

   @AfterEach
   void tearDown() {
      server.stop( 0 );
   }

   private void record( final HttpExchange exchange ) {
      authorizationByPath.put( exchange.getRequestURI().getPath(),
            String.valueOf( exchange.getRequestHeaders().getFirst( "Authorization" ) ) );
   }

   private static void respond( final HttpExchange exchange, final int status, final byte[] body ) throws IOException {
      exchange.sendResponseHeaders( status, body.length == 0 ? -1 : body.length );
      if ( body.length > 0 ) {
         try ( final OutputStream out = exchange.getResponseBody() ) {
            out.write( body );
         }
      }
      exchange.close();
   }

   private static byte[] zip( final Map<String, String> files ) throws IOException {
      final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try ( final ZipOutputStream zip = new ZipOutputStream( bytes ) ) {
         for ( final Map.Entry<String, String> file : files.entrySet() ) {
            zip.putNextEntry( new ZipEntry( file.getKey() ) );
            zip.write( file.getValue().getBytes( StandardCharsets.UTF_8 ) );
            zip.closeEntry();
         }
      }
      return bytes.toByteArray();
   }

   private Path sourceDirectory() {
      return configService.getGithubSourceDirectory( configService.getGithubSource( sourceId ) );
   }

   @Test
   void checkFindsTheRemoteCommitWithoutDownloading() {
      final GithubSourceStatus status = service.check( sourceId, null );

      assertEquals( SHA_1, status.remoteSha() );
      assertFalse( status.synced() );
      assertTrue( status.updateAvailable() );
      assertNull( status.lastError() );
      assertFalse( Files.exists( sourceDirectory() ) );
      assertTrue( configService.getReadablePaths().stream().noneMatch( path -> path.equals( sourceDirectory() ) ) );
   }

   @Test
   void syncExtractsOnlyTheModelsOfTheDirectoryAndSendsTheTokenOnlyToGithub() throws IOException {
      final GithubSourceStatus status = service.sync( sourceId, TOKEN );

      assertTrue( status.synced() );
      assertEquals( SHA_1, status.syncedSha() );
      assertFalse( status.updateAvailable() );
      assertEquals( "A1", Files.readString( sourceDirectory().resolve( "org.example/1.0.0/A.ttl" ) ) );
      assertTrue( Files.exists( sourceDirectory().resolve( "org.example/1.0.0/B.ttl" ) ) );
      assertFalse( Files.exists( sourceDirectory().resolve( "README.md" ) ) );
      assertFalse( Files.exists( sourceDirectory().resolve( "C.ttl" ) ) );
      assertTrue( Files.exists( sourceDirectory().resolve( GithubSourceService.METADATA_FILE ) ) );
      assertTrue( configService.getReadablePaths().contains( sourceDirectory() ) );
      assertTrue( configService.isReadOnly( sourceDirectory().resolve( "org.example/1.0.0/A.ttl" ) ) );

      assertEquals( "Bearer " + TOKEN, authorizationByPath.get( "/repos/owner/repo/commits/main" ) );
      assertEquals( "Bearer " + TOKEN, authorizationByPath.get( "/repos/owner/repo/zipball/" + SHA_1 ) );
      assertEquals( "null", authorizationByPath.get( "/archive/" + SHA_1 + ".zip" ) );
   }

   @Test
   void syncReplacesTheLocalCopyAndCheckReportsUpdates() throws IOException {
      service.sync( sourceId, null );
      currentSha = SHA_2;

      final GithubSourceStatus checked = service.check( sourceId, null );
      assertTrue( checked.updateAvailable() );
      assertEquals( SHA_1, checked.syncedSha() );
      assertEquals( "A1", Files.readString( sourceDirectory().resolve( "org.example/1.0.0/A.ttl" ) ) );

      final GithubSourceStatus synced = service.sync( sourceId, null );
      assertEquals( SHA_2, synced.syncedSha() );
      assertFalse( synced.updateAvailable() );
      assertEquals( "A2", Files.readString( sourceDirectory().resolve( "org.example/1.0.0/A.ttl" ) ) );
      assertFalse( Files.exists( sourceDirectory().resolve( "org.example/1.0.0/B.ttl" ) ) );
      try ( final var children = Files.list( configService.getGithubCachePath() ) ) {
         assertEquals( List.of( sourceDirectory() ), children.toList() );
      }
   }

   @Test
   void checkReportsErrorsInsteadOfThrowing() {
      commitStatus = 404;

      final GithubSourceStatus status = service.check( sourceId, null );

      assertTrue( status.lastError().contains( "not found" ) );
      assertFalse( status.updateAvailable() );
   }

   @Test
   void failedSyncKeepsTheExistingCopy() throws IOException {
      service.sync( sourceId, null );
      commitStatus = 401;

      final GithubSourceException exception = assertThrows( GithubSourceException.class, () -> service.sync( sourceId, TOKEN ) );

      assertTrue( exception.getMessage().contains( "access token" ) );
      assertFalse( exception.getMessage().contains( TOKEN ) );
      assertEquals( "A1", Files.readString( sourceDirectory().resolve( "org.example/1.0.0/A.ttl" ) ) );
      assertEquals( exception.getMessage(), service.getStatus( sourceId ).lastError() );
   }

   @Test
   void extractRejectsPathsOutsideTheTarget() throws IOException {
      final Path archive = tempDir.resolve( "evil.zip" );
      Files.write( archive, zip( Map.of( "top/models/../../../evil.ttl", "evil" ) ) );

      assertThrows( GithubSourceException.class, () -> GithubSourceService.extract( archive, "", tempDir.resolve( "target" ) ) );
      assertFalse( Files.exists( tempDir.resolve( "evil.ttl" ) ) );
   }

   @Test
   void extractReportsAMissingDirectory() throws IOException {
      final Path archive = tempDir.resolve( "repo.zip" );
      Files.write( archive, zip( Map.of( "top/models/A.ttl", "A" ) ) );

      final GithubSourceException exception = assertThrows( GithubSourceException.class,
            () -> GithubSourceService.extract( archive, "missing", tempDir.resolve( "target" ) ) );
      assertTrue( exception.getMessage().contains( "missing" ) );
   }

   @Test
   void describesRateLimits() {
      final GithubSourceConfig source = configService.getGithubSource( sourceId );
      final HttpHeaders headers = HttpHeaders.of( Map.of( "x-ratelimit-remaining", List.of( "0" ) ), ( name, value ) -> true );

      assertTrue( GithubSourceService.describeError( source, 403, headers ).contains( "rate limit" ) );
   }

   @Test
   void environmentProxyHonorsNoProxy() {
      final var selector = GithubSourceService.EnvironmentProxySelector.from(
            Map.of( "HTTPS_PROXY", "http://proxy.example:3128", "NO_PROXY", "localhost,.corp.example" ) ).orElseThrow();

      assertEquals( Proxy.Type.HTTP, selector.select( URI.create( "https://api.github.com" ) ).getFirst().type() );
      assertEquals( Proxy.NO_PROXY, selector.select( URI.create( "https://github.corp.example" ) ).getFirst() );
      assertEquals( Proxy.NO_PROXY, selector.select( URI.create( "http://localhost:9090" ) ).getFirst() );
      assertTrue( GithubSourceService.EnvironmentProxySelector.from( Map.of() ).isEmpty() );
   }
}
