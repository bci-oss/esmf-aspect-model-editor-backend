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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.esmf.ame.plugin.api.AmePlugin;
import org.eclipse.esmf.ame.services.models.plugins.InstalledPlugin;

/**
 * Builds plugin archives for the tests. The backend part is a JAR containing a plugin class of the test sources, so it
 * is loaded through the isolated plugin class loader like a real plugin.
 */
final class TestPluginArchives {
   static final String MAIN_JS = "customElements.define('ame-plugin-test', class extends HTMLElement {});";

   private TestPluginArchives() {
   }

   /**
    * @return a manifest with a user interface, an import command and a generate command
    */
   static String manifest( final String id, final String version ) {
      return """
            {
              "id": "%s",
              "name": "Plugin %s",
              "version": "%s",
              "description": "A plugin for the tests",
              "pluginApiVersion": 1,
              "frontend": { "entry": "frontend/main.js", "element": "ame-plugin-test" },
              "contributions": { "commands": [
                { "id": "open", "kind": "import", "label": "Open", "labels": { "de": "Öffnen" } },
                { "id": "summary", "kind": "generate", "label": "Summary", "icon": "summarize",
                  "placements": [ "menu", "toolbar" ], "menu": "plugins" } ] }
            }
            """.formatted( id, id, version );
   }

   /**
    * @return an archive with a user interface and a backend part implemented by the given class
    */
   static byte[] backendPlugin( final String id, final String version, final Class<? extends AmePlugin> pluginClass ) {
      final Map<String, byte[]> files = frontendFiles( id, version );
      files.put( "backend/plugin.jar", pluginJar( pluginClass ) );
      return zip( files );
   }

   /**
    * @return an archive with a user interface only
    */
   static byte[] frontendPlugin( final String id, final String version ) {
      return zip( frontendFiles( id, version ) );
   }

   /**
    * Installs an archive the way {@link PluginService#install} does, but without a running registry.
    */
   static void installDirectly( final PluginStorage storage, final String id, final String version, final boolean enabled,
         final byte[] archive ) {
      final Path staging = storage.createStagingDirectory();
      try {
         PluginArchive.extract( new ByteArrayInputStream( archive ), staging );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( e );
      }
      storage.moveToVersionDirectory( staging, id, version );
      final Map<String, InstalledPlugin> state = storage.readState();
      state.put( id, new InstalledPlugin( version, enabled ) );
      storage.writeState( state );
   }

   static byte[] zip( final Map<String, byte[]> files ) {
      final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try ( final ZipOutputStream zip = new ZipOutputStream( bytes ) ) {
         for ( final Map.Entry<String, byte[]> file : files.entrySet() ) {
            zip.putNextEntry( new ZipEntry( file.getKey() ) );
            zip.write( file.getValue() );
            zip.closeEntry();
         }
      } catch ( final IOException e ) {
         throw new UncheckedIOException( e );
      }
      return bytes.toByteArray();
   }

   private static Map<String, byte[]> frontendFiles( final String id, final String version ) {
      final Map<String, byte[]> files = new LinkedHashMap<>();
      files.put( PluginManifestReader.MANIFEST, manifest( id, version ).getBytes( StandardCharsets.UTF_8 ) );
      files.put( "frontend/main.js", MAIN_JS.getBytes( StandardCharsets.UTF_8 ) );
      return files;
   }

   private static byte[] pluginJar( final Class<? extends AmePlugin> pluginClass ) {
      final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try ( final JarOutputStream jar = new JarOutputStream( bytes ) ) {
         for ( final Path classFile : classFiles( pluginClass ) ) {
            jar.putNextEntry( new JarEntry( pluginClass.getPackageName().replace( '.', '/' ) + "/" + classFile.getFileName() ) );
            jar.write( Files.readAllBytes( classFile ) );
            jar.closeEntry();
         }
         jar.putNextEntry( new JarEntry( "META-INF/services/" + AmePlugin.class.getName() ) );
         jar.write( pluginClass.getName().getBytes( StandardCharsets.UTF_8 ) );
         jar.closeEntry();
      } catch ( final IOException e ) {
         throw new UncheckedIOException( e );
      }
      return bytes.toByteArray();
   }

   private static List<Path> classFiles( final Class<?> type ) throws IOException {
      final Path packageDirectory;
      try {
         packageDirectory = Path.of( type.getProtectionDomain().getCodeSource().getLocation().toURI() )
               .resolve( type.getPackageName().replace( '.', '/' ) );
      } catch ( final URISyntaxException e ) {
         throw new IllegalStateException( e );
      }
      try ( final Stream<Path> files = Files.list( packageDirectory ) ) {
         return files.filter( file -> {
            final String name = file.getFileName().toString();
            return name.equals( type.getSimpleName() + ".class" ) || name.startsWith( type.getSimpleName() + "$" );
         } ).toList();
      }
   }
}
