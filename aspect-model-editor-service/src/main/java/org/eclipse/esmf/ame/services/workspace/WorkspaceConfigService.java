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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.esmf.ame.config.ApplicationSettings;
import org.eclipse.esmf.ame.exceptions.FileNotFoundException;
import org.eclipse.esmf.ame.services.models.workspace.WorkspaceConfig;

import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for managing workspace directories and persisting the workspace configuration.
 */
@Singleton
public class WorkspaceConfigService {
   private static final Logger LOG = LoggerFactory.getLogger( WorkspaceConfigService.class );

   private final Path configFilePath;
   private final Path defaultWorkspacePath;
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
    * Returns the currently active workspace directory.
    *
    * @return the active workspace path
    */
   public synchronized Path getActiveWorkspacePath() {
      return Path.of( currentConfig.activeWorkspace() ).toAbsolutePath().normalize();
   }

   /**
    * Returns the list of all configured workspace paths.
    *
    * @return list of workspace paths
    */
   public synchronized List<Path> getWorkspacePaths() {
      return currentConfig.workspaces().stream()
            .map( pathStr -> Path.of( pathStr ).toAbsolutePath().normalize() )
            .toList();
   }

   /**
    * Adds a new workspace directory to the configuration.
    *
    * @param pathStr the path to the workspace directory
    * @return updated workspace configuration
    */
   public synchronized WorkspaceConfig addWorkspace( final String pathStr ) {
      return addWorkspace( pathStr, false );
   }

   /**
    * Adds a new workspace directory to the configuration.
    *
    * @param pathStr the path to the workspace directory
    * @param makeActive whether to set this new workspace as active
    * @return updated workspace configuration
    */
   public synchronized WorkspaceConfig addWorkspace( final String pathStr, final boolean makeActive ) {
      final Path normalizedPath = validateAndNormalizePath( pathStr );
      ensureDirectoryExists( normalizedPath );

      final String normalizedPathStr = normalizedPath.toString();
      final List<String> updatedWorkspaces = new ArrayList<>( currentConfig.workspaces() );

      if ( !updatedWorkspaces.contains( normalizedPathStr ) ) {
         updatedWorkspaces.add( normalizedPathStr );
      }

      final String newActive = makeActive || updatedWorkspaces.size() == 1
            ? normalizedPathStr
            : currentConfig.activeWorkspace();

      currentConfig = new WorkspaceConfig( updatedWorkspaces, newActive );
      saveConfig( currentConfig );
      LOG.info( "Added workspace: {} (active: {})", normalizedPathStr, newActive.equals( normalizedPathStr ) );
      return currentConfig;
   }

   /**
    * Sets the active workspace directory.
    *
    * @param pathStr the path of the workspace to make active
    * @return updated workspace configuration
    */
   public synchronized WorkspaceConfig setActiveWorkspace( final String pathStr ) {
      final Path normalizedPath = validateAndNormalizePath( pathStr );
      final String normalizedPathStr = normalizedPath.toString();

      if ( !currentConfig.workspaces().contains( normalizedPathStr ) ) {
         throw new IllegalArgumentException( "Workspace path is not registered: " + normalizedPathStr );
      }

      ensureDirectoryExists( normalizedPath );

      currentConfig = new WorkspaceConfig( currentConfig.workspaces(), normalizedPathStr );
      saveConfig( currentConfig );
      LOG.info( "Set active workspace to: {}", normalizedPathStr );
      return currentConfig;
   }

   /**
    * Removes a workspace directory from the configuration.
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

      final String newActive = currentConfig.activeWorkspace().equals( normalizedPathStr )
            ? updatedWorkspaces.getFirst()
            : currentConfig.activeWorkspace();

      currentConfig = new WorkspaceConfig( updatedWorkspaces, newActive );
      saveConfig( currentConfig );
      LOG.info( "Removed workspace: {} (new active: {})", normalizedPathStr, newActive );
      return currentConfig;
   }

   /**
    * Resets the workspace configuration to the default single workspace.
    *
    * @return updated workspace configuration
    */
   public synchronized WorkspaceConfig resetToDefault() {
      currentConfig = createDefaultConfig();
      saveConfig( currentConfig );
      LOG.info( "Reset workspace configuration to default: {}", defaultWorkspacePath );
      return currentConfig;
   }

   private WorkspaceConfig loadConfig() {
      if ( Files.exists( configFilePath ) ) {
         try {
            final String json = Files.readString( configFilePath, StandardCharsets.UTF_8 );
            final WorkspaceConfig config = jsonMapper.readValue( json, WorkspaceConfig.class );
            if ( config != null && config.workspaces() != null && !config.workspaces().isEmpty() ) {
               final List<String> workspaces = new ArrayList<>( config.workspaces() );
               final String defaultStr = defaultWorkspacePath.toString();
               if ( !workspaces.contains( defaultStr ) ) {
                  workspaces.add( 0, defaultStr );
               }
               final String active = config.activeWorkspace() != null && workspaces.contains( config.activeWorkspace() )
                     ? config.activeWorkspace()
                     : defaultStr;
               return new WorkspaceConfig( workspaces, active );
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
      return new WorkspaceConfig( List.of( defaultStr ), defaultStr );
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
