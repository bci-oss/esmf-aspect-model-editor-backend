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
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.esmf.ame.exceptions.PluginConflictException;
import org.eclipse.esmf.ame.exceptions.PluginExecutionException;
import org.eclipse.esmf.ame.plugin.api.AmePlugin;
import org.eclipse.esmf.ame.plugin.api.GenerationResult;
import org.eclipse.esmf.ame.plugin.api.GenerationSource;
import org.eclipse.esmf.ame.plugin.api.ImportResult;
import org.eclipse.esmf.ame.plugin.api.ImportSource;
import org.eclipse.esmf.ame.plugin.api.ModelGenerator;
import org.eclipse.esmf.ame.plugin.api.ModelImporter;
import org.eclipse.esmf.ame.plugin.api.PluginAction;
import org.eclipse.esmf.ame.plugin.api.PluginException;
import org.eclipse.esmf.ame.plugin.api.WorkspaceAccess;
import org.eclipse.esmf.ame.services.models.plugins.InstalledPlugin;
import org.eclipse.esmf.ame.services.models.plugins.PluginManifest;

import io.micronaut.context.annotation.Context;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the enabled plugins when the backend starts and stops them when it stops.
 * <p>
 * Plugins are loaded once: installing, enabling, disabling and removing plugins takes effect after a restart of the
 * backend. A plugin that cannot be loaded is reported as failed and does not prevent the backend from starting.
 * Each call into a plugin runs with the class loader of the plugin as context class loader, as libraries expect.
 * </p>
 */
@Context
public class PluginRegistry {
   private static final Logger LOG = LoggerFactory.getLogger( PluginRegistry.class );

   private final PluginStorage storage;
   private final PluginManifestReader manifestReader;
   private final WorkspaceAccess workspaceAccess;
   private volatile Map<String, LoadedPlugin> plugins = Map.of();

   public PluginRegistry( final PluginStorage storage, final PluginManifestReader manifestReader, final WorkspaceAccess workspaceAccess ) {
      this.storage = storage;
      this.manifestReader = manifestReader;
      this.workspaceAccess = workspaceAccess;
   }

   /**
    * Removes leftovers of earlier installations and loads the enabled plugins.
    */
   @PostConstruct
   public synchronized void start() {
      try {
         storage.removeLeftovers();
         final Map<String, LoadedPlugin> loaded = new LinkedHashMap<>();
         for ( final Map.Entry<String, InstalledPlugin> installed : storage.readState().entrySet() ) {
            if ( installed.getValue().enabled() ) {
               loaded.put( installed.getKey(), load( installed.getKey(), installed.getValue().version() ) );
            }
         }
         plugins = Map.copyOf( loaded );
      } catch ( final RuntimeException e ) {
         LOG.error( "Could not load the plugins", e );
      }
   }

   /**
    * Stops all plugins and releases their class loaders.
    */
   @PreDestroy
   public synchronized void stop() {
      plugins.values().forEach( PluginRegistry::stop );
      plugins = Map.of();
   }

   /**
    * @return the plugins loaded when the backend started, including the failed ones
    */
   public Collection<LoadedPlugin> plugins() {
      return plugins.values();
   }

   /**
    * @param id the id of the plugin
    * @return the plugin if the backend tried to load it when it started
    */
   public Optional<LoadedPlugin> find( final String id ) {
      return Optional.ofNullable( plugins.get( id ) );
   }

   /**
    * Calls an action of a running plugin.
    *
    * @param pluginId the id of the plugin
    * @param actionId the id of the action
    * @param payload the JSON payload
    * @return the JSON result of the action
    * @throws PluginConflictException if the plugin is not running
    * @throws NoSuchElementException if the plugin has no such action
    * @throws PluginExecutionException if the action fails
    */
   public String runAction( final String pluginId, final String actionId, final String payload ) {
      final String result = invoke( pluginId, "action", actionId, LoadedPlugin::action, action -> action.handle( payload ) );
      return result == null ? "null" : result;
   }

   /**
    * Calls an importer of a running plugin.
    *
    * @param pluginId the id of the plugin
    * @param importerId the id of the importer
    * @param source the file to import
    * @return the result of the import
    * @throws PluginConflictException if the plugin is not running
    * @throws NoSuchElementException if the plugin has no such importer
    * @throws PluginExecutionException if the import fails
    */
   public ImportResult runImporter( final String pluginId, final String importerId, final ImportSource source ) {
      return requireResult( pluginId, "importer", importerId,
            invoke( pluginId, "importer", importerId, LoadedPlugin::importer, importer -> importer.importModel( source ) ) );
   }

   /**
    * Calls a generator of a running plugin.
    *
    * @param pluginId the id of the plugin
    * @param generatorId the id of the generator
    * @param source the Aspect Model to generate files from
    * @return the generated files
    * @throws PluginConflictException if the plugin is not running
    * @throws NoSuchElementException if the plugin has no such generator
    * @throws PluginExecutionException if the generation fails
    */
   public GenerationResult runGenerator( final String pluginId, final String generatorId, final GenerationSource source ) {
      return requireResult( pluginId, "generator", generatorId,
            invoke( pluginId, "generator", generatorId, LoadedPlugin::generator, generator -> generator.generate( source ) ) );
   }

   /**
    * Looks up an extension of a running plugin and calls it with the class loader of the plugin.
    */
   private <E, R> R invoke( final String pluginId, final String kind, final String extensionId,
         final BiFunction<LoadedPlugin, String, Optional<E>> lookup, final ExtensionCall<E, R> call ) {
      final LoadedPlugin plugin = requireActive( pluginId );
      final E extension = lookup.apply( plugin, extensionId ).orElseThrow(
            () -> new NoSuchElementException( "The plugin '" + pluginId + "' has no " + kind + " '" + extensionId + "'." ) );
      return call( plugin, kind + " '" + extensionId + "'", () -> call.call( extension ) );
   }

   private static <R> R requireResult( final String pluginId, final String kind, final String extensionId, final R result ) {
      if ( result == null ) {
         throw PluginExecutionException.failed( "The " + kind + " '" + extensionId + "' of plugin '" + pluginId + "' returned no result.",
               null );
      }
      return result;
   }

   private LoadedPlugin requireActive( final String pluginId ) {
      final LoadedPlugin plugin = plugins.get( pluginId );
      if ( plugin == null || !plugin.isActive() ) {
         throw new PluginConflictException( "The plugin '" + pluginId + "' is not running. Make sure it is installed and enabled, "
               + "then restart the editor." );
      }
      return plugin;
   }

   private LoadedPlugin load( final String id, final String version ) {
      final Path directory = storage.versionDirectory( id, version );
      final PluginManifest manifest;
      try {
         manifest = manifestReader.read( directory );
      } catch ( final RuntimeException e ) {
         return failed( id, version, directory, null, e.getMessage(), e );
      }
      if ( !id.equals( manifest.id() ) || !version.equals( manifest.version() ) ) {
         return failed( id, version, directory, manifest, "The installed files belong to " + manifest.id() + " " + manifest.version(), null );
      }
      if ( !PluginManifestReader.hasBackend( directory ) ) {
         LOG.info( "Loaded plugin {} {} (frontend only)", id, version );
         return LoadedPlugin.active( manifest, directory, null );
      }

      final PluginClassLoader classLoader = PluginClassLoader.create( id, directory.resolve( PluginManifestReader.BACKEND ) );
      final AmePlugin plugin;
      try {
         plugin = withContextClassLoader( classLoader, () -> single( ServiceLoader.load( AmePlugin.class, classLoader ).stream()
               .map( ServiceLoader.Provider::get ).toList() ) );
      } catch ( final Exception | ServiceConfigurationError | LinkageError e ) {
         close( id, classLoader );
         return failed( id, version, directory, manifest, describe( e ), e );
      }
      try {
         final LoadedPlugin.Runtime runtime = startRuntime( id, version, plugin, classLoader );
         LOG.info( "Loaded plugin {} {}", id, version );
         return LoadedPlugin.active( manifest, directory, runtime );
      } catch ( final Exception | ServiceConfigurationError | LinkageError e ) {
         stopQuietly( id, plugin, classLoader );
         close( id, classLoader );
         return failed( id, version, directory, manifest, describe( e ), e );
      }
   }

   private LoadedPlugin.Runtime startRuntime( final String id, final String version, final AmePlugin plugin,
         final PluginClassLoader classLoader ) throws Exception {
      withContextClassLoader( classLoader, () -> {
         plugin.start( new DefaultPluginContext( id, version, storage.dataDirectory( id ), workspaceAccess, new PluginLogger( id ) ) );
         return null;
      } );
      return new LoadedPlugin.Runtime( plugin, classLoader, index( plugin.actions(), PluginAction::id, "action" ),
            index( plugin.importers(), ModelImporter::id, "importer" ), index( plugin.generators(), ModelGenerator::id, "generator" ) );
   }

   private static AmePlugin single( final List<AmePlugin> implementations ) throws PluginException {
      if ( implementations.size() != 1 ) {
         throw new PluginException( "The backend JARs must register exactly one implementation of " + AmePlugin.class.getName()
               + " in META-INF/services, but " + implementations.size() + " were found." );
      }
      return implementations.getFirst();
   }

   private static <T> Map<String, T> index( final List<T> items, final Function<T, String> id, final String kind ) throws PluginException {
      final Map<String, T> indexed = new LinkedHashMap<>();
      for ( final T item : items ) {
         if ( indexed.put( id.apply( item ), item ) != null ) {
            throw new PluginException( "The plugin defines the " + kind + " '" + id.apply( item ) + "' more than once." );
         }
      }
      return indexed;
   }

   private static LoadedPlugin failed( final String id, final String version, final Path directory, final PluginManifest manifest,
         final String error, final Throwable cause ) {
      LOG.error( "Could not load plugin {} {}: {}", id, version, error, cause );
      return LoadedPlugin.failed( id, version, directory, manifest, error );
   }

   private static <T> T call( final LoadedPlugin plugin, final String target, final PluginCall<T> call ) {
      final PluginClassLoader classLoader = plugin.runtime() == null ? null : plugin.runtime().classLoader();
      try {
         return withContextClassLoader( classLoader, call );
      } catch ( final PluginException e ) {
         throw PluginExecutionException.rejected( e.getMessage(), e );
      } catch ( final Exception | LinkageError e ) {
         throw PluginExecutionException.failed( "The " + target + " of plugin '" + plugin.id() + "' failed: " + describe( e ), e );
      }
   }

   private static <T> T withContextClassLoader( final ClassLoader classLoader, final PluginCall<T> call ) throws Exception {
      final Thread thread = Thread.currentThread();
      final ClassLoader previous = thread.getContextClassLoader();
      thread.setContextClassLoader( classLoader == null ? previous : classLoader );
      try {
         return call.call();
      } finally {
         thread.setContextClassLoader( previous );
      }
   }

   private static void stop( final LoadedPlugin plugin ) {
      if ( plugin.runtime() != null ) {
         stopQuietly( plugin.id(), plugin.runtime().plugin(), plugin.runtime().classLoader() );
         close( plugin.id(), plugin.runtime().classLoader() );
      }
   }

   private static void stopQuietly( final String id, final AmePlugin plugin, final PluginClassLoader classLoader ) {
      try {
         withContextClassLoader( classLoader, () -> {
            plugin.stop();
            return null;
         } );
      } catch ( final Exception | LinkageError e ) {
         LOG.warn( "Plugin {} failed to stop", id, e );
      }
   }

   private static void close( final String id, final PluginClassLoader classLoader ) {
      try {
         classLoader.close();
      } catch ( final IOException e ) {
         LOG.warn( "Could not close the class loader of plugin {}", id, e );
      }
   }

   private static String describe( final Throwable e ) {
      return e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
   }

   /**
    * A call into a plugin.
    *
    * @param <T> the type of the result
    */
   @FunctionalInterface
   private interface PluginCall<T> {
      T call() throws Exception;
   }

   /**
    * A call of an extension (action, importer or generator) of a plugin.
    *
    * @param <E> the type of the extension
    * @param <R> the type of the result
    */
   @FunctionalInterface
   private interface ExtensionCall<E, R> {
      R call( E extension ) throws Exception;
   }
}
