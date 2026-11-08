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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.esmf.ame.exceptions.InvalidPluginException;
import org.eclipse.esmf.ame.services.models.plugins.PluginManifest;
import org.eclipse.esmf.ame.services.models.plugins.PluginManifest.CommandContribution;

import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginManifestReaderTest {
   @TempDir
   Path pluginDirectory;

   private final PluginManifestReader reader = new PluginManifestReader( JsonMapper.createDefault() );

   @BeforeEach
   void setUp() throws IOException {
      Files.createDirectories( pluginDirectory.resolve( "frontend" ) );
      Files.writeString( pluginDirectory.resolve( "frontend/main.js" ), TestPluginArchives.MAIN_JS );
   }

   @Test
   void readsValidManifest() throws IOException {
      writeManifest( TestPluginArchives.manifest( "example-import", "1.0.0" ) );

      final PluginManifest manifest = reader.read( pluginDirectory );

      assertEquals( "example-import", manifest.id() );
      assertEquals( "1.0.0", manifest.version() );
      assertEquals( "ame-plugin-test", manifest.frontend().element() );
      assertEquals( "Öffnen", manifest.commands().getFirst().labels().get( "de" ) );
      final CommandContribution summary = manifest.commands().get( 1 );
      assertEquals( "generate", summary.kind() );
      assertEquals( "summarize", summary.icon() );
      assertEquals( List.of( "menu", "toolbar" ), summary.placements() );
      assertEquals( "plugins", summary.menu() );
   }

   @Test
   void defaultsCommandPlacementMenuAndIconByKind() throws IOException {
      writeManifest( """
            { "id": "demo", "name": "Demo", "version": "1.0.0", "pluginApiVersion": 1,
              "frontend": { "entry": "frontend/main.js", "element": "ame-plugin-demo" },
              "contributions": { "commands": [ { "id": "in", "kind": "import", "label": "In" },
                                               { "id": "out", "kind": "generate", "label": "Out" } ] } }
            """ );

      final List<CommandContribution> commands = reader.read( pluginDirectory ).commands();

      assertEquals( List.of( "menu" ), commands.get( 0 ).placements() );
      assertEquals( "file/import", commands.get( 0 ).menu() );
      assertEquals( "upload_file", commands.get( 0 ).icon() );
      assertEquals( "generate", commands.get( 1 ).menu() );
      assertEquals( "output", commands.get( 1 ).icon() );
   }

   @Test
   void rejectsInvalidCommands() throws IOException {
      writeManifest( """
            { "id": "demo", "name": "Demo", "version": "1.0.0", "pluginApiVersion": 1,
              "frontend": { "entry": "frontend/main.js", "element": "ame-plugin-demo" },
              "contributions": { "commands": [
                { "id": "edit", "kind": "edit", "label": "Edit" },
                { "id": "in", "kind": "import", "label": "In", "placements": [ "contextMenu" ] },
                { "id": "out", "kind": "generate", "label": "Out", "icon": "<svg>", "menu": "view",
                  "placements": [ "toolbar", "statusbar", "toolbar" ] },
                { "id": "twice", "kind": "generate", "label": "Twice", "placements": [ "toolbar", "toolbar" ] } ] } }
            """ );

      final String message = assertThrows( InvalidPluginException.class, () -> reader.read( pluginDirectory ) ).getMessage();

      assertTrue( message.contains( "the 'kind' of the command 'edit' must be one of import, generate" ), message );
      assertTrue( message.contains( "the 'placements' of the command 'in' must be any of menu, toolbar" ), message );
      assertTrue( message.contains( "the 'icon' of the command 'out'" ), message );
      assertTrue( message.contains( "the 'placements' of the command 'out' must be any of menu, toolbar" ), message );
      assertTrue( message.contains( "the 'menu' of the command 'out' must be one of file/import, generate, plugins" ), message );
      assertTrue( message.contains( "the 'placements' of the command 'twice' contain a placement more than once" ), message );
   }

   @Test
   void rejectsMissingManifest() {
      final InvalidPluginException exception = assertThrows( InvalidPluginException.class, () -> reader.read( pluginDirectory ) );

      assertTrue( exception.getMessage().contains( "'plugin.json' is missing" ) );
   }

   @Test
   void rejectsMalformedJson() throws IOException {
      writeManifest( "{ not json" );

      final InvalidPluginException exception = assertThrows( InvalidPluginException.class, () -> reader.read( pluginDirectory ) );

      assertTrue( exception.getMessage().contains( "not valid JSON" ) );
   }

   @Test
   void reportsAllProblemsAtOnce() throws IOException {
      writeManifest( """
            { "id": "Invalid Id", "version": "../1", "pluginApiVersion": 1,
              "frontend": { "entry": "../outside.js", "element": "nohyphen" },
              "contributions": { "commands": [ { "id": "a", "kind": "import", "label": "A", "menu": "toolbar" },
                                               { "id": "a", "kind": "import", "label": "" } ] } }
            """ );

      final String message = assertThrows( InvalidPluginException.class, () -> reader.read( pluginDirectory ) ).getMessage();

      assertTrue( message.contains( "'id'" ), message );
      assertTrue( message.contains( "'name' is missing" ), message );
      assertTrue( message.contains( "'version'" ), message );
      assertTrue( message.contains( "'frontend.entry'" ), message );
      assertTrue( message.contains( "'frontend.element'" ), message );
      assertTrue( message.contains( "'menu' of the command 'a'" ), message );
      assertTrue( message.contains( "'a' is used more than once" ), message );
      assertTrue( message.contains( "has no 'label'" ), message );
   }

   @Test
   void rejectsUnsupportedApiVersion() throws IOException {
      writeManifest( """
            { "id": "demo", "name": "Demo", "version": "1.0.0", "pluginApiVersion": 99,
              "frontend": { "entry": "frontend/main.js", "element": "ame-plugin-demo" } }
            """ );

      final String message = assertThrows( InvalidPluginException.class, () -> reader.read( pluginDirectory ) ).getMessage();

      assertTrue( message.contains( "built for plugin API 99" ), message );
   }

   @Test
   void rejectsFrontendEntryOutsideFrontendDirectory() throws IOException {
      Files.writeString( pluginDirectory.resolve( "main.js" ), "" );
      writeManifest( """
            { "id": "demo", "name": "Demo", "version": "1.0.0", "pluginApiVersion": 1,
              "frontend": { "entry": "main.js", "element": "ame-plugin-demo" } }
            """ );

      final String message = assertThrows( InvalidPluginException.class, () -> reader.read( pluginDirectory ) ).getMessage();

      assertTrue( message.contains( "'frontend.entry'" ), message );
   }

   @Test
   void rejectsCommandsWithoutFrontend() throws IOException {
      Files.createDirectories( pluginDirectory.resolve( "backend" ) );
      Files.writeString( pluginDirectory.resolve( "backend/plugin.jar" ), "" );
      writeManifest( """
            { "id": "demo", "name": "Demo", "version": "1.0.0", "pluginApiVersion": 1,
              "contributions": { "commands": [ { "id": "open", "kind": "import", "label": "Open" } ] } }
            """ );

      final String message = assertThrows( InvalidPluginException.class, () -> reader.read( pluginDirectory ) ).getMessage();

      assertTrue( message.contains( "commands need a 'frontend'" ), message );
   }

   @Test
   void rejectsPluginWithoutBackendAndFrontend() throws IOException {
      writeManifest( """
            { "id": "demo", "name": "Demo", "version": "1.0.0", "pluginApiVersion": 1 }
            """ );

      final String message = assertThrows( InvalidPluginException.class, () -> reader.read( pluginDirectory ) ).getMessage();

      assertTrue( message.contains( "neither JARs" ), message );
   }

   private void writeManifest( final String json ) throws IOException {
      Files.writeString( pluginDirectory.resolve( PluginManifestReader.MANIFEST ), json );
   }
}
