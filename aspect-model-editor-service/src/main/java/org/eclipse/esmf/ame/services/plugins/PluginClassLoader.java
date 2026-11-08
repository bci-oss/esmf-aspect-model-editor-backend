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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.plugin.api.AmePlugin;

/**
 * The class loader of a plugin: it loads the JARs of the plugin and shares nothing with the editor but the Java runtime,
 * the plugin API and the SLF4J API. Plugins can therefore bring any library in any version without conflicting with the
 * editor or other plugins, and their log messages end up in the log of the editor.
 */
final class PluginClassLoader extends URLClassLoader {
   static final String API_PACKAGE = AmePlugin.class.getPackageName() + ".";
   static final String SLF4J_PACKAGE = "org.slf4j.";

   /**
    * The packages a plugin gets from the editor instead of from its own JARs.
    */
   static final List<String> SHARED_PACKAGES = List.of( API_PACKAGE, SLF4J_PACKAGE );

   private PluginClassLoader( final String pluginId, final URL[] jars ) {
      super( "plugin-" + pluginId, jars, new ApiOnlyClassLoader( AmePlugin.class.getClassLoader() ) );
   }

   /**
    * Creates the class loader for the backend part of an extracted plugin.
    *
    * @param pluginId the id of the plugin
    * @param backendDirectory the directory containing the JARs of the plugin
    * @return the class loader
    */
   static PluginClassLoader create( final String pluginId, final Path backendDirectory ) {
      final URL[] urls = backendJars( backendDirectory ).stream().map( PluginClassLoader::toUrl ).toArray( URL[]::new );
      return new PluginClassLoader( pluginId, urls );
   }

   /**
    * Lists the JARs of the backend part of a plugin, including those in subdirectories such as {@code lib/}.
    *
    * @param backendDirectory the directory containing the JARs of the plugin
    * @return the JARs, sorted by path; empty if the directory does not exist
    */
   static List<Path> backendJars( final Path backendDirectory ) {
      if ( !Files.isDirectory( backendDirectory ) ) {
         return List.of();
      }
      try ( final Stream<Path> files = Files.walk( backendDirectory ) ) {
         return files.filter( Files::isRegularFile ).filter( file -> file.getFileName().toString().endsWith( ".jar" ) )
               .sorted( Comparator.naturalOrder() ).toList();
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not list the JARs of the plugin in " + backendDirectory, e );
      }
   }

   private static URL toUrl( final Path jar ) {
      try {
         return jar.toUri().toURL();
      } catch ( final MalformedURLException e ) {
         throw new IllegalStateException( e );
      }
   }

   /**
    * The parent of all plugin class loaders: it loads the classes of the Java runtime and of the {@link #SHARED_PACKAGES
    * shared packages}, but none of the other classes of the editor or its libraries.
    */
   static final class ApiOnlyClassLoader extends ClassLoader {
      private final ClassLoader apiClassLoader;

      ApiOnlyClassLoader( final ClassLoader apiClassLoader ) {
         super( "ame-plugin-api", ClassLoader.getPlatformClassLoader() );
         this.apiClassLoader = apiClassLoader;
      }

      @Override
      protected Class<?> loadClass( final String name, final boolean resolve ) throws ClassNotFoundException {
         if ( isShared( name ) ) {
            return apiClassLoader.loadClass( name );
         }
         return super.loadClass( name, resolve );
      }

      private static boolean isShared( final String className ) {
         return SHARED_PACKAGES.stream().anyMatch( className::startsWith );
      }
   }
}
