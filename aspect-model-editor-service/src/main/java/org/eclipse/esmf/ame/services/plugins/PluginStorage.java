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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.config.ApplicationSettings;
import org.eclipse.esmf.ame.services.models.plugins.InstalledPlugin;

import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The files of the installed plugins.
 * <p>
 * Layout of the plugins directory:
 * </p>
 * <pre>
 * plugins/
 *    plugins.json                      the installed version of each plugin and whether it is enabled
 *    &lt;id&gt;/versions/&lt;version&gt;/       the extracted plugin archive
 *    &lt;id&gt;/data/                       the data directory of the plugin
 *    .staging-&lt;uuid&gt;/                 an archive being installed
 * </pre>
 * <p>
 * Files of a running plugin cannot be removed on every platform, so outdated versions and removed plugins are only
 * deleted by {@link #removeLeftovers()} when the backend starts, before any plugin is loaded.
 * </p>
 */
@Singleton
public class PluginStorage {
   static final String STATE_FILE = "plugins.json";
   private static final String VERSIONS = "versions";
   private static final String DATA = "data";
   private static final String STAGING_PREFIX = ".staging-";
   private static final Argument<Map<String, InstalledPlugin>> STATE_TYPE = Argument.mapOf( String.class, InstalledPlugin.class );

   private static final Logger LOG = LoggerFactory.getLogger( PluginStorage.class );

   private final Path pluginsPath;
   private final JsonMapper jsonMapper;

   @Inject
   public PluginStorage( final JsonMapper jsonMapper ) {
      this( ApplicationSettings.getPluginsPath(), jsonMapper );
   }

   public PluginStorage( final Path pluginsPath, final JsonMapper jsonMapper ) {
      this.pluginsPath = pluginsPath;
      this.jsonMapper = jsonMapper;
   }

   /**
    * Reads the installed plugins.
    *
    * @return the installed version and enabled flag by plugin id, sorted by id
    */
   public synchronized Map<String, InstalledPlugin> readState() {
      final Path stateFile = pluginsPath.resolve( STATE_FILE );
      if ( !Files.isRegularFile( stateFile ) ) {
         return new TreeMap<>();
      }
      try {
         final Map<String, InstalledPlugin> state = jsonMapper.readValue( Files.readString( stateFile, StandardCharsets.UTF_8 ), STATE_TYPE );
         return state == null ? new TreeMap<>() : new TreeMap<>( state );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not read the installed plugins from " + stateFile, e );
      }
   }

   /**
    * Writes the installed plugins.
    *
    * @param state the installed version and enabled flag by plugin id
    */
   public synchronized void writeState( final Map<String, InstalledPlugin> state ) {
      try {
         Files.createDirectories( pluginsPath );
         final Path temporary = pluginsPath.resolve( STATE_FILE + ".tmp" );
         Files.writeString( temporary, jsonMapper.writeValueAsString( new TreeMap<>( state ) ), StandardCharsets.UTF_8 );
         Files.move( temporary, pluginsPath.resolve( STATE_FILE ), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not save the installed plugins", e );
      }
   }

   /**
    * Reads the installed plugins, applies a change and writes them back. Nothing is written if the change fails.
    *
    * @param change changes the installed plugins and returns a result
    * @param <T> the type of the result
    * @return the result of the change
    */
   public synchronized <T> T updateState( final Function<Map<String, InstalledPlugin>, T> change ) {
      final Map<String, InstalledPlugin> state = readState();
      final T result = change.apply( state );
      writeState( state );
      return result;
   }

   /**
    * @param id the id of the plugin
    * @param version the version of the plugin
    * @return the directory the archive of the plugin version is extracted to
    */
   public Path versionDirectory( final String id, final String version ) {
      return pluginDirectory( id ).resolve( VERSIONS ).resolve( version );
   }

   /**
    * Returns the data directory of a plugin and creates it if needed.
    *
    * @param id the id of the plugin
    * @return the data directory
    */
   public Path dataDirectory( final String id ) {
      final Path directory = pluginDirectory( id ).resolve( DATA );
      try {
         Files.createDirectories( directory );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not create the data directory of plugin " + id, e );
      }
      return directory;
   }

   /**
    * Creates an empty directory to extract an archive into before it is validated.
    *
    * @return the new directory
    */
   public Path createStagingDirectory() {
      try {
         return Files.createDirectories( pluginsPath.resolve( STAGING_PREFIX + UUID.randomUUID() ) );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not create a directory to extract the plugin into", e );
      }
   }

   /**
    * Moves an extracted and validated archive to the directory of its version, replacing an existing one.
    *
    * @param staging the directory the archive was extracted to
    * @param id the id of the plugin
    * @param version the version of the plugin
    */
   public void moveToVersionDirectory( final Path staging, final String id, final String version ) {
      final Path target = versionDirectory( id, version );
      try {
         deleteRecursively( target );
         Files.createDirectories( target.getParent() );
         Files.move( staging, target, StandardCopyOption.ATOMIC_MOVE );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not install plugin " + id + " " + version, e );
      }
   }

   /**
    * Deletes a staging directory, e.g. after the archive extracted to it turned out to be invalid.
    *
    * @param staging the directory created by {@link #createStagingDirectory()}
    */
   public void discardStagingDirectory( final Path staging ) {
      deleteQuietly( staging );
   }

   /**
    * Deletes all files of a plugin, including its data.
    *
    * @param id the id of the plugin
    */
   public void deletePlugin( final String id ) {
      deleteQuietly( pluginDirectory( id ) );
   }

   /**
    * Deletes interrupted installations, versions which are not installed anymore and removed plugins. Must only be
    * called while no plugin is loaded.
    */
   public synchronized void removeLeftovers() {
      final Map<String, InstalledPlugin> state = readState();
      for ( final Path directory : directories( pluginsPath ) ) {
         final String name = directory.getFileName().toString();
         final InstalledPlugin installed = state.get( name );
         if ( installed == null ) {
            LOG.info( "Removing leftover plugin directory {}", directory );
            deleteQuietly( directory );
            continue;
         }
         directories( directory.resolve( VERSIONS ) ).stream()
               .filter( version -> !version.getFileName().toString().equals( installed.version() ) )
               .forEach( version -> {
                  LOG.info( "Removing outdated plugin version {}", version );
                  deleteQuietly( version );
               } );
      }
   }

   private Path pluginDirectory( final String id ) {
      final Path directory = pluginsPath.resolve( id ).normalize();
      if ( !pluginsPath.normalize().equals( directory.getParent() ) ) {
         throw new IllegalArgumentException( "Invalid plugin id: " + id );
      }
      return directory;
   }

   private static List<Path> directories( final Path parent ) {
      if ( !Files.isDirectory( parent ) ) {
         return List.of();
      }
      try ( final Stream<Path> entries = Files.list( parent ) ) {
         return entries.filter( Files::isDirectory ).toList();
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not read " + parent, e );
      }
   }

   private static void deleteRecursively( final Path directory ) throws IOException {
      if ( Files.exists( directory ) ) {
         FileUtils.deleteDirectory( directory.toFile() );
      }
   }

   private static void deleteQuietly( final Path directory ) {
      FileUtils.deleteQuietly( directory.toFile() );
   }
}
