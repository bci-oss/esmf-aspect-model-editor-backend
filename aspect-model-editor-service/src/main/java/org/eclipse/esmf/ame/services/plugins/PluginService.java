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

package org.eclipse.esmf.ame.services.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import org.eclipse.esmf.ame.exceptions.PluginConflictException;
import org.eclipse.esmf.ame.exceptions.PluginExecutionException;
import org.eclipse.esmf.ame.plugin.api.GeneratedFile;
import org.eclipse.esmf.ame.plugin.api.GenerationResult;
import org.eclipse.esmf.ame.plugin.api.ImportSource;
import org.eclipse.esmf.ame.plugin.api.PluginApi;
import org.eclipse.esmf.ame.services.models.plugins.InstalledPlugin;
import org.eclipse.esmf.ame.services.models.plugins.PluginGenerationRequest;
import org.eclipse.esmf.ame.services.models.plugins.PluginGenerationResult;
import org.eclipse.esmf.ame.services.models.plugins.PluginImportResult;
import org.eclipse.esmf.ame.services.models.plugins.PluginManifest;
import org.eclipse.esmf.ame.services.models.plugins.PluginState;
import org.eclipse.esmf.ame.services.models.plugins.PluginStatus;
import org.eclipse.esmf.ame.services.models.plugins.PluginsOverview;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Installs, removes, enables and disables plugins and calls the running ones.
 * <p>
 * Changes are written to the plugins directory right away but only take effect after a restart of the backend, see
 * {@link PluginRegistry}. The status of each plugin therefore tells what is running now and whether a restart is needed.
 * </p>
 */
@Singleton
public class PluginService {
   private static final Logger LOG = LoggerFactory.getLogger( PluginService.class );

   private final PluginStorage storage;
   private final PluginManifestReader manifestReader;
   private final PluginRegistry registry;
   private final Set<String> installedSinceStart = new HashSet<>();

   public PluginService( final PluginStorage storage, final PluginManifestReader manifestReader, final PluginRegistry registry ) {
      this.storage = storage;
      this.manifestReader = manifestReader;
      this.registry = registry;
   }

   /**
    * Lists the installed plugins.
    *
    * @return the installed plugins and whether a restart is needed
    */
   public synchronized PluginsOverview getOverview() {
      final Map<String, InstalledPlugin> state = storage.readState();
      final List<PluginStatus> plugins = state.entrySet().stream()
            .map( installed -> status( installed.getKey(), installed.getValue() ) )
            .sorted( Comparator.comparing( ( PluginStatus plugin ) -> plugin.name().toLowerCase( Locale.ROOT ) ).thenComparing( PluginStatus::id ) )
            .toList();
      final boolean removedButRunning = registry.plugins().stream().anyMatch( plugin -> plugin.isActive() && !state.containsKey( plugin.id() ) );
      return new PluginsOverview( PluginApi.VERSION, plugins,
            removedButRunning || plugins.stream().anyMatch( PluginStatus::restartRequired ) );
   }

   /**
    * Installs a plugin archive, replacing an installed version of the plugin. The plugin is enabled and starts with the
    * next start of the backend.
    *
    * @param archive the content of the ZIP archive
    * @return the status of the installed plugin
    * @throws org.eclipse.esmf.ame.exceptions.InvalidPluginException if the archive is not a valid plugin
    * @throws PluginConflictException if the same version of the plugin is running
    */
   public synchronized PluginStatus install( final InputStream archive ) {
      final Path staging = storage.createStagingDirectory();
      try {
         PluginArchive.extract( archive, staging );
         final PluginManifest manifest = manifestReader.read( staging );
         requireNotRunning( manifest );
         storage.moveToVersionDirectory( staging, manifest.id(), manifest.version() );

         final InstalledPlugin installed = new InstalledPlugin( manifest.version(), true );
         storage.updateState( state -> state.put( manifest.id(), installed ) );
         installedSinceStart.add( manifest.id() );
         LOG.info( "Installed plugin {} {}", manifest.id(), manifest.version() );
         return status( manifest.id(), installed );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not extract the plugin", e );
      } finally {
         storage.discardStagingDirectory( staging );
      }
   }

   /**
    * Removes a plugin including its data. The files of a running plugin are deleted with the next start of the backend.
    *
    * @param id the id of the plugin
    * @throws NoSuchElementException if the plugin is not installed
    */
   public synchronized void uninstall( final String id ) {
      storage.updateState( state -> Optional.ofNullable( state.remove( id ) ).orElseThrow( () -> notInstalled( id ) ) );
      installedSinceStart.remove( id );
      if ( registry.find( id ).filter( LoadedPlugin::isActive ).isEmpty() ) {
         storage.deletePlugin( id );
      }
      LOG.info( "Removed plugin {}", id );
   }

   /**
    * Enables or disables a plugin, effective with the next start of the backend.
    *
    * @param id the id of the plugin
    * @param enabled whether the plugin starts with the backend
    * @return the status of the plugin
    * @throws NoSuchElementException if the plugin is not installed
    */
   public synchronized PluginStatus setEnabled( final String id, final boolean enabled ) {
      final InstalledPlugin changed = storage.updateState( state -> {
         final InstalledPlugin current = Optional.ofNullable( state.get( id ) ).orElseThrow( () -> notInstalled( id ) );
         final InstalledPlugin updated = new InstalledPlugin( current.version(), enabled );
         state.put( id, updated );
         return updated;
      } );
      return status( id, changed );
   }

   /**
    * Calls an action of a running plugin.
    *
    * @param pluginId the id of the plugin
    * @param actionId the id of the action
    * @param payload the JSON payload
    * @return the JSON result
    */
   public String runAction( final String pluginId, final String actionId, final String payload ) {
      return registry.runAction( pluginId, actionId, payload );
   }

   /**
    * Calls an importer of a running plugin.
    *
    * @param pluginId the id of the plugin
    * @param importerId the id of the importer
    * @param fileName the name of the uploaded file
    * @param content the content of the uploaded file
    * @param options the options chosen in the user interface of the plugin
    * @return the result of the import
    */
   public PluginImportResult importModel( final String pluginId, final String importerId, final String fileName, final byte[] content,
         final Map<String, String> options ) {
      return PluginImportResult.of( registry.runImporter( pluginId, importerId, new ImportSource( fileName, content, options ) ) );
   }

   /**
    * Creates files from an Aspect Model with a generator of a running plugin. The plugin gets a copy of the model and
    * cannot change the model in the editor.
    *
    * @param pluginId the id of the plugin
    * @param generatorId the id of the generator
    * @param request the Aspect Model open in the editor and the options chosen in the user interface of the plugin
    * @return the generated files
    * @throws IllegalArgumentException if the request contains no complete model
    * @throws PluginExecutionException if the generator fails or creates a file with an invalid name
    */
   public PluginGenerationResult generate( final String pluginId, final String generatorId, final PluginGenerationRequest request ) {
      final GenerationResult result = registry.runGenerator( pluginId, generatorId, request.toGenerationSource() );
      requirePlainFileNames( pluginId, generatorId, result );
      return PluginGenerationResult.of( result );
   }

   private static void requirePlainFileNames( final String pluginId, final String generatorId, final GenerationResult result ) {
      for ( final GeneratedFile file : result.files() ) {
         if ( !isPlainFileName( file.fileName() ) ) {
            throw PluginExecutionException.failed( "The generator '" + generatorId + "' of plugin '" + pluginId
                  + "' created a file with the invalid name '" + file.fileName() + "'; names must not contain directories.", null );
         }
      }
   }

   private static boolean isPlainFileName( final String name ) {
      return !name.isBlank() && !name.contains( "/" ) && !name.contains( "\\" ) && !".".equals( name ) && !"..".equals( name );
   }

   /**
    * Resolves a file of the user interface of a running plugin.
    *
    * @param pluginId the id of the plugin
    * @param path the path of the file below the {@value PluginManifestReader#FRONTEND} directory of the plugin
    * @return the file
    * @throws NoSuchElementException if the plugin is not running, has no user interface or no such file
    */
   public Path frontendFile( final String pluginId, final String path ) {
      final LoadedPlugin plugin = registry.find( pluginId )
            .filter( LoadedPlugin::isActive )
            .filter( loaded -> loaded.manifest() != null && loaded.manifest().frontend() != null )
            .orElseThrow( () -> new NoSuchElementException( "The plugin '" + pluginId + "' is not running or has no user interface." ) );
      final Path frontendDirectory = plugin.directory().resolve( PluginManifestReader.FRONTEND ).normalize();
      final Path file = frontendDirectory.resolve( path ).normalize();
      if ( !file.startsWith( frontendDirectory ) || !Files.isRegularFile( file ) ) {
         throw new NoSuchElementException( "The plugin '" + pluginId + "' has no file '" + path + "'." );
      }
      return file;
   }

   private void requireNotRunning( final PluginManifest manifest ) {
      final boolean running = registry.find( manifest.id() )
            .filter( plugin -> plugin.isActive() && plugin.runtime() != null && plugin.version().equals( manifest.version() ) )
            .isPresent();
      if ( running ) {
         throw new PluginConflictException( "Version " + manifest.version() + " of plugin '" + manifest.id() + "' is running and cannot be "
               + "replaced. Increase the version of the plugin, or remove it and restart the editor before installing it again." );
      }
   }

   private PluginStatus status( final String id, final InstalledPlugin installed ) {
      final Optional<LoadedPlugin> loaded = registry.find( id );
      final boolean runsInstalledVersion = loaded.filter( plugin -> plugin.version().equals( installed.version() ) ).isPresent();
      final PluginManifest manifest = loaded.filter( plugin -> runsInstalledVersion && plugin.manifest() != null )
            .map( LoadedPlugin::manifest )
            .orElseGet( () -> readInstalledManifest( id, installed ) );
      final PluginManifest running = loaded.filter( LoadedPlugin::isActive ).map( LoadedPlugin::manifest ).orElse( manifest );

      return new PluginStatus(
            id,
            manifest != null && manifest.name() != null ? manifest.name() : id,
            installed.version(),
            manifest == null ? null : manifest.description(),
            loaded.map( LoadedPlugin::state ).orElse( PluginState.INACTIVE ),
            installed.enabled(),
            isRestartRequired( id, installed, loaded, runsInstalledVersion ),
            loaded.map( LoadedPlugin::error ).orElse( manifest == null ? "The installed files of the plugin are missing or invalid." : null ),
            running == null ? null : running.frontend(),
            running == null ? List.of() : running.commands(),
            loaded.map( LoadedPlugin::actionIds ).orElse( List.of() ),
            loaded.map( LoadedPlugin::importerIds ).orElse( List.of() ),
            loaded.map( LoadedPlugin::generatorIds ).orElse( List.of() ) );
   }

   private boolean isRestartRequired( final String id, final InstalledPlugin installed, final Optional<LoadedPlugin> loaded,
         final boolean runsInstalledVersion ) {
      return installed.enabled()
            ? !runsInstalledVersion || installedSinceStart.contains( id )
            : loaded.filter( LoadedPlugin::isActive ).isPresent();
   }

   private @Nullable PluginManifest readInstalledManifest( final String id, final InstalledPlugin installed ) {
      try {
         return manifestReader.read( storage.versionDirectory( id, installed.version() ) );
      } catch ( final RuntimeException e ) {
         LOG.warn( "Could not read the manifest of plugin {} {}: {}", id, installed.version(), e.getMessage() );
         return null;
      }
   }

   private static NoSuchElementException notInstalled( final String id ) {
      return new NoSuchElementException( "The plugin '" + id + "' is not installed." );
   }
}
