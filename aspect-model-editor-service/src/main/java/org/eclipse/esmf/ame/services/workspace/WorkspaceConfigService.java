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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.config.ApplicationSettings;
import org.eclipse.esmf.ame.exceptions.FileNotFoundException;
import org.eclipse.esmf.ame.exceptions.ReadOnlySourceException;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceConfig;
import org.eclipse.esmf.ame.services.models.workspace.ModelSource;
import org.eclipse.esmf.ame.services.models.workspace.WorkspaceConfig;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the model sources of the editor and persists them in {@code workspaces.json}:
 * <ul>
 *    <li>local workspace directories; the default workspace is always the first one and cannot be removed</li>
 *    <li>read-only GitHub sources, whose local copies are stored in {@code github/} next to the default workspace</li>
 * </ul>
 * Models are resolved in the order of {@link #getSources()}: workspaces first, then GitHub sources.
 */
@Singleton
public class WorkspaceConfigService {
   public static final String GITHUB_CACHE_DIRECTORY = "github";

   private static final Logger LOG = LoggerFactory.getLogger( WorkspaceConfigService.class );
   private static final Pattern HOST_PATTERN = Pattern.compile( "[A-Za-z0-9]([A-Za-z0-9.\\-]*[A-Za-z0-9])?(:\\d{1,5})?" );
   private static final Pattern NAME_PATTERN = Pattern.compile( "[A-Za-z0-9_.\\-]+" );
   private static final Pattern REF_PATTERN = Pattern.compile( "[A-Za-z0-9_.][A-Za-z0-9_.\\-/]*" );
   private static final Pattern DIRECTORY_SEGMENT_PATTERN = Pattern.compile( "[A-Za-z0-9_.\\- ]+" );

   private final Path configFilePath;
   private final Path defaultWorkspacePath;
   private final Path githubCachePath;
   private final JsonMapper jsonMapper;

   private WorkspaceConfig currentConfig;

   public WorkspaceConfigService() {
      this( ApplicationSettings.getWorkspaceConfigFilePath(),
            ApplicationSettings.getMetaModelStoragePath(),
            JsonMapper.createDefault() );
   }

   public WorkspaceConfigService( final Path configFilePath, final Path defaultWorkspacePath, final JsonMapper jsonMapper ) {
      this.configFilePath = configFilePath;
      this.defaultWorkspacePath = defaultWorkspacePath.toAbsolutePath().normalize();
      this.githubCachePath = this.defaultWorkspacePath.resolveSibling( GITHUB_CACHE_DIRECTORY );
      this.jsonMapper = jsonMapper;
      this.currentConfig = loadConfig();
   }

   /**
    * Gets the current workspace configuration.
    *
    * @return the workspace configuration
    */
   public synchronized WorkspaceConfig getConfig() {
      return currentConfig;
   }

   /**
    * Returns the default workspace path.
    *
    * @return the default workspace path
    */
   public Path getDefaultWorkspacePath() {
      return defaultWorkspacePath;
   }

   /**
    * Returns the directory containing the local copies of all GitHub sources.
    *
    * @return the GitHub cache directory
    */
   public Path getGithubCachePath() {
      return githubCachePath;
   }

   /**
    * Returns the writable workspace directories, the default workspace first.
    *
    * @return list of workspace paths
    */
   public synchronized List<Path> getWorkspacePaths() {
      return currentConfig.workspaces().stream()
            .map( pathStr -> Path.of( pathStr ).toAbsolutePath().normalize() )
            .toList();
   }

   /**
    * Returns all sources models are read from, in resolution order: the workspaces, then the enabled GitHub sources
    * that have been synchronized at least once.
    *
    * @return the model sources
    */
   public synchronized List<ModelSource> getSources() {
      final List<ModelSource> sources = new ArrayList<>();
      for ( final Path workspace : getWorkspacePaths() ) {
         final boolean isDefault = workspace.equals( defaultWorkspacePath );
         final String name = isDefault || workspace.getFileName() == null ? defaultName( workspace ) : workspace.getFileName().toString();
         sources.add( new ModelSource( ModelSource.WORKSPACE, workspace.toString(), name, workspace.toString(), false, isDefault ) );
      }
      for ( final GithubSourceConfig github : currentConfig.githubSources() ) {
         final Path directory = getGithubSourceDirectory( github );
         if ( github.enabled() && Files.isDirectory( directory ) ) {
            sources.add( new ModelSource( ModelSource.GITHUB, github.id(), github.displayName(), directory.toString(), true, false ) );
         }
      }
      return sources;
   }

   /**
    * Returns the root directories of all sources in resolution order.
    *
    * @return the readable source directories
    */
   public List<Path> getReadablePaths() {
      return getSources().stream().map( ModelSource::root ).toList();
   }

   /**
    * Finds the source containing a file.
    *
    * @param file the file
    * @return the source, empty if the file belongs to no source
    */
   public Optional<ModelSource> findSource( final Path file ) {
      final Path normalized = file.toAbsolutePath().normalize();
      return getSources().stream()
            .filter( source -> source.contains( normalized ) )
            .max( Comparator.comparingInt( source -> source.root().getNameCount() ) );
   }

   /**
    * Whether a file belongs to a read-only source. Every file below the GitHub cache directory is read-only, even if
    * its source is disabled or has been removed.
    *
    * @param file the file
    * @return {@code true} if the file must not be changed
    */
   public boolean isReadOnly( final Path file ) {
      return file.toAbsolutePath().normalize().startsWith( githubCachePath );
   }

   /**
    * Ensures that a file may be changed.
    *
    * @param file the file to change
    * @throws ReadOnlySourceException if the file belongs to a read-only source
    */
   public void requireWritable( final Path file ) {
      if ( isReadOnly( file ) ) {
         throw new ReadOnlySourceException( "Models of GitHub sources are read-only: " + file.getFileName() );
      }
   }

   /**
    * Resolves the workspace to write to.
    *
    * @param requested the requested workspace path, {@code null} or blank for the default workspace
    * @return the workspace path
    * @throws IllegalArgumentException if the requested workspace is not registered
    */
   public synchronized Path resolveTargetWorkspace( final @Nullable String requested ) {
      if ( requested == null || requested.isBlank() ) {
         return defaultWorkspacePath;
      }
      final Path normalized = validateAndNormalizePath( requested );
      if ( !currentConfig.workspaces().contains( normalized.toString() ) ) {
         throw new IllegalArgumentException( "Workspace is not registered: " + normalized );
      }
      return normalized;
   }

   /**
    * Adds a new workspace directory to the configuration.
    *
    * @param pathStr the path to the workspace directory
    * @return updated workspace configuration
    * @throws IllegalArgumentException if the directory overlaps with another workspace or the GitHub cache
    */
   public synchronized WorkspaceConfig addWorkspace( final String pathStr ) {
      final Path normalizedPath = validateAndNormalizePath( pathStr );
      final String normalizedPathStr = normalizedPath.toString();
      if ( currentConfig.workspaces().contains( normalizedPathStr ) ) {
         return currentConfig;
      }

      requireNoOverlap( normalizedPath );
      ensureDirectoryExists( normalizedPath );
      final List<String> updatedWorkspaces = new ArrayList<>( currentConfig.workspaces() );
      updatedWorkspaces.add( normalizedPathStr );

      currentConfig = currentConfig.withWorkspaces( updatedWorkspaces );
      saveConfig( currentConfig );
      LOG.info( "Added workspace: {}", normalizedPathStr );
      return currentConfig;
   }

   /**
    * Removes a workspace directory from the configuration. The directory itself is not touched.
    *
    * @param pathStr the path to remove
    * @return updated workspace configuration
    */
   public synchronized WorkspaceConfig removeWorkspace( final String pathStr ) {
      final Path normalizedPath = validateAndNormalizePath( pathStr );
      final String normalizedPathStr = normalizedPath.toString();

      if ( normalizedPath.equals( defaultWorkspacePath ) ) {
         throw new IllegalArgumentException( "The default home workspace cannot be removed: " + normalizedPathStr );
      }

      if ( !currentConfig.workspaces().contains( normalizedPathStr ) ) {
         throw new FileNotFoundException( "Workspace path not found: " + normalizedPathStr );
      }

      if ( currentConfig.workspaces().size() <= 1 ) {
         throw new IllegalStateException( "Cannot remove the only remaining workspace: " + normalizedPathStr );
      }

      final List<String> updatedWorkspaces = new ArrayList<>( currentConfig.workspaces() );
      updatedWorkspaces.remove( normalizedPathStr );

      currentConfig = currentConfig.withWorkspaces( updatedWorkspaces );
      saveConfig( currentConfig );
      LOG.info( "Removed workspace: {}", normalizedPathStr );
      return currentConfig;
   }

   /**
    * Resets the workspaces to the default workspace. GitHub sources are kept.
    *
    * @return updated workspace configuration
    */
   public synchronized WorkspaceConfig resetToDefault() {
      currentConfig = createDefaultConfig().withGithubSources( currentConfig.githubSources() );
      saveConfig( currentConfig );
      LOG.info( "Reset workspace configuration to default: {}", defaultWorkspacePath );
      return currentConfig;
   }

   /**
    * Returns the configured GitHub source.
    *
    * @param id the source id
    * @return the source
    * @throws FileNotFoundException if no source has the id
    */
   public synchronized GithubSourceConfig getGithubSource( final String id ) {
      return currentConfig.githubSources().stream()
            .filter( source -> source.id().equals( id ) )
            .findFirst()
            .orElseThrow( () -> new FileNotFoundException( "GitHub source not found: " + id ) );
   }

   /**
    * Returns the directory of the local copy of a GitHub source.
    *
    * @param source the source
    * @return the directory below {@link #getGithubCachePath()}
    */
   public Path getGithubSourceDirectory( final GithubSourceConfig source ) {
      final Path directory = githubCachePath.resolve( source.id() ).normalize();
      if ( !directory.startsWith( githubCachePath ) || directory.equals( githubCachePath ) ) {
         throw new IllegalArgumentException( "Invalid GitHub source id: " + source.id() );
      }
      return directory;
   }

   /**
    * Adds a GitHub source. Its models are available after the first synchronization.
    *
    * @param request the source; the id is ignored and derived from the other values
    * @return updated workspace configuration
    * @throws IllegalArgumentException if a value is invalid or the source is already configured
    */
   public synchronized WorkspaceConfig addGithubSource( final GithubSourceConfig request ) {
      final GithubSourceConfig source = normalizeGithubSource( request );
      if ( currentConfig.githubSources().stream().anyMatch( existing -> existing.id().equals( source.id() ) ) ) {
         throw new IllegalArgumentException( "GitHub source is already configured: " + source.displayName() );
      }
      final List<GithubSourceConfig> sources = new ArrayList<>( currentConfig.githubSources() );
      sources.add( source );
      currentConfig = currentConfig.withGithubSources( sources );
      saveConfig( currentConfig );
      LOG.info( "Added GitHub source: {}", source.displayName() );
      return currentConfig;
   }

   /**
    * Updates a GitHub source. If repository, ref or directory change, the source gets a new id and its old local
    * copy is deleted, so it has to be synchronized again.
    *
    * @param id the id of the source to update
    * @param request the new values
    * @return updated workspace configuration
    */
   public synchronized WorkspaceConfig updateGithubSource( final String id, final GithubSourceConfig request ) {
      final GithubSourceConfig existing = getGithubSource( id );
      final GithubSourceConfig updated = normalizeGithubSource( request );
      if ( !updated.id().equals( id ) && currentConfig.githubSources().stream().anyMatch( other -> other.id().equals( updated.id() ) ) ) {
         throw new IllegalArgumentException( "GitHub source is already configured: " + updated.displayName() );
      }
      final List<GithubSourceConfig> sources = currentConfig.githubSources().stream()
            .map( source -> source.id().equals( id ) ? updated : source )
            .toList();
      currentConfig = currentConfig.withGithubSources( sources );
      saveConfig( currentConfig );
      if ( !updated.id().equals( id ) ) {
         deleteGithubSourceDirectory( existing );
      }
      LOG.info( "Updated GitHub source: {}", updated.displayName() );
      return currentConfig;
   }

   /**
    * Removes a GitHub source and deletes its local copy.
    *
    * @param id the source id
    * @return updated workspace configuration
    */
   public synchronized WorkspaceConfig removeGithubSource( final String id ) {
      final GithubSourceConfig existing = getGithubSource( id );
      final List<GithubSourceConfig> sources = currentConfig.githubSources().stream()
            .filter( source -> !source.id().equals( id ) )
            .toList();
      currentConfig = currentConfig.withGithubSources( sources );
      saveConfig( currentConfig );
      deleteGithubSourceDirectory( existing );
      LOG.info( "Removed GitHub source: {}", existing.displayName() );
      return currentConfig;
   }

   private GithubSourceConfig normalizeGithubSource( final GithubSourceConfig request ) {
      if ( request == null ) {
         throw new IllegalArgumentException( "GitHub source must not be empty." );
      }
      final String host = trimToEmpty( request.host() ).toLowerCase( Locale.ROOT );
      final String owner = trimToEmpty( request.owner() );
      final String repository = stripGitSuffix( trimToEmpty( request.repository() ) );
      final String refType = trimToEmpty( request.refType() ).toLowerCase( Locale.ROOT );
      final String ref = trimToEmpty( request.ref() );
      final String directory = normalizeDirectory( request.directory() );

      if ( !host.isEmpty() && !HOST_PATTERN.matcher( host ).matches() ) {
         throw new IllegalArgumentException( "Invalid GitHub host: " + host );
      }
      requireName( owner, "owner" );
      requireName( repository, "repository" );
      if ( !refType.isEmpty() && !GithubSourceConfig.BRANCH.equals( refType ) && !GithubSourceConfig.TAG.equals( refType ) ) {
         throw new IllegalArgumentException( "The ref type must be 'branch' or 'tag': " + refType );
      }
      if ( !REF_PATTERN.matcher( ref ).matches() || ref.contains( ".." ) || ref.endsWith( "/" ) ) {
         throw new IllegalArgumentException( "Invalid branch or tag: " + ref );
      }

      final GithubSourceConfig withoutId = new GithubSourceConfig( null, host, owner, repository, refType, ref, directory,
            request.enabled() );
      return new GithubSourceConfig( githubSourceId( withoutId ), withoutId.host(), owner, repository, withoutId.refType(), ref,
            directory, request.enabled() );
   }

   private static String githubSourceId( final GithubSourceConfig source ) {
      final StringBuilder id = new StringBuilder();
      if ( !GithubSourceConfig.DEFAULT_HOST.equals( source.host() ) ) {
         id.append( source.host() ).append( '_' );
      }
      id.append( source.owner() ).append( '_' ).append( source.repository() ).append( '_' ).append( source.ref() );
      if ( !source.directory().isEmpty() ) {
         id.append( '_' ).append( source.directory() );
      }
      return id.toString().replaceAll( "[^A-Za-z0-9_.\\-]", "-" );
   }

   private static String normalizeDirectory( final @Nullable String directory ) {
      final String trimmed = trimToEmpty( directory ).replace( '\\', '/' );
      final List<String> segments = Stream.of( trimmed.split( "/" ) ).filter( segment -> !segment.isBlank() ).toList();
      for ( final String segment : segments ) {
         if ( ".".equals( segment ) || "..".equals( segment ) || !DIRECTORY_SEGMENT_PATTERN.matcher( segment ).matches() ) {
            throw new IllegalArgumentException( "Invalid directory: " + directory );
         }
      }
      return String.join( "/", segments );
   }

   private static void requireName( final String value, final String label ) {
      if ( !NAME_PATTERN.matcher( value ).matches() || ".".equals( value ) || "..".equals( value ) ) {
         throw new IllegalArgumentException( "Invalid GitHub " + label + ": " + value );
      }
   }

   private static String stripGitSuffix( final String repository ) {
      return repository.endsWith( ".git" ) ? repository.substring( 0, repository.length() - 4 ) : repository;
   }

   private static String trimToEmpty( final @Nullable String value ) {
      return value == null ? "" : value.trim();
   }

   private void deleteGithubSourceDirectory( final GithubSourceConfig source ) {
      final Path directory = getGithubSourceDirectory( source );
      if ( !Files.exists( directory ) ) {
         return;
      }
      try ( final Stream<Path> paths = Files.walk( directory ) ) {
         for ( final Path path : paths.sorted( Comparator.reverseOrder() ).toList() ) {
            Files.deleteIfExists( path );
         }
      } catch ( final IOException | UncheckedIOException e ) {
         LOG.warn( "Could not delete the local copy of GitHub source {}: {}", source.id(), e.getMessage() );
      }
   }

   private void requireNoOverlap( final Path path ) {
      if ( path.startsWith( githubCachePath ) || githubCachePath.startsWith( path ) ) {
         throw new IllegalArgumentException( "A workspace must not contain or be inside the GitHub cache directory: " + path );
      }
      for ( final Path workspace : getWorkspacePaths() ) {
         if ( path.startsWith( workspace ) || workspace.startsWith( path ) ) {
            throw new IllegalArgumentException( "A workspace must not contain or be inside another workspace: " + workspace );
         }
      }
   }

   private String defaultName( final Path workspace ) {
      return workspace.getFileName() != null ? workspace.getFileName().toString() : workspace.toString();
   }

   private WorkspaceConfig loadConfig() {
      if ( Files.exists( configFilePath ) ) {
         try {
            final String json = Files.readString( configFilePath, StandardCharsets.UTF_8 );
            final WorkspaceConfig config = jsonMapper.readValue( json, WorkspaceConfig.class );
            if ( config != null && !config.workspaces().isEmpty() ) {
               final String defaultStr = defaultWorkspacePath.toString();
               final LinkedHashSet<String> workspaces = new LinkedHashSet<>();
               workspaces.add( defaultStr );
               config.workspaces().stream()
                     .map( path -> Path.of( path ).toAbsolutePath().normalize().toString() )
                     .forEach( workspaces::add );
               final List<GithubSourceConfig> githubSources = config.githubSources().stream()
                     .filter( source -> source != null && source.id() != null && !source.id().isBlank() )
                     .toList();
               return new WorkspaceConfig( WorkspaceConfig.CURRENT_VERSION, List.copyOf( workspaces ), githubSources );
            }
         } catch ( final Exception e ) {
            LOG.warn( "Failed to load workspace configuration from {}, falling back to default.", configFilePath, e );
         }
      }

      final WorkspaceConfig defaultConfig = createDefaultConfig();
      saveConfig( defaultConfig );
      return defaultConfig;
   }

   private void saveConfig( final WorkspaceConfig config ) {
      try {
         final Path parent = configFilePath.getParent();
         if ( parent != null && !Files.exists( parent ) ) {
            Files.createDirectories( parent );
         }
         final String json = jsonMapper.writeValueAsString( config );
         Files.writeString( configFilePath, json, StandardCharsets.UTF_8 );
      } catch ( final IOException e ) {
         LOG.error( "Failed to save workspace configuration to {}", configFilePath, e );
      }
   }

   private WorkspaceConfig createDefaultConfig() {
      final String defaultStr = defaultWorkspacePath.toString();
      ensureDirectoryExists( defaultWorkspacePath );
      return new WorkspaceConfig( List.of( defaultStr ) );
   }

   private Path validateAndNormalizePath( final String pathStr ) {
      if ( pathStr == null || pathStr.isBlank() ) {
         throw new IllegalArgumentException( "Workspace path must not be null or empty." );
      }
      return Path.of( pathStr.trim() ).toAbsolutePath().normalize();
   }

   private void ensureDirectoryExists( final Path path ) {
      try {
         if ( !Files.exists( path ) ) {
            Files.createDirectories( path );
         }
      } catch ( final IOException e ) {
         LOG.warn( "Could not create workspace directory: {}", path, e );
      }
   }
}
