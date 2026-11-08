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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.esmf.ame.exceptions.InvalidPluginException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginArchiveTest {
   @TempDir
   Path tempDir;

   @Test
   void extractsNestedFiles() throws IOException {
      final Path target = Files.createDirectories( tempDir.resolve( "target" ) );

      PluginArchive.extract( new ByteArrayInputStream( TestPluginArchives.frontendPlugin( "demo", "1.0.0" ) ), target );

      assertTrue( Files.isRegularFile( target.resolve( "plugin.json" ) ) );
      assertEquals( TestPluginArchives.MAIN_JS, Files.readString( target.resolve( "frontend/main.js" ) ) );
   }

   @Test
   void rejectsEntriesLeavingTheTargetDirectory() throws IOException {
      final Path target = Files.createDirectories( tempDir.resolve( "target" ) );
      final byte[] archive = TestPluginArchives.zip( Map.of( "../evil.txt", "evil".getBytes( StandardCharsets.UTF_8 ) ) );

      final InvalidPluginException exception = assertThrows( InvalidPluginException.class,
            () -> PluginArchive.extract( new ByteArrayInputStream( archive ), target ) );

      assertTrue( exception.getMessage().contains( "unsafe path" ) );
      assertFalse( Files.exists( tempDir.resolve( "evil.txt" ) ) );
   }

   @Test
   void rejectsFilesWhichAreNoZipArchives() throws IOException {
      final Path target = Files.createDirectories( tempDir.resolve( "target" ) );
      final byte[] notAZip = "just text".getBytes( StandardCharsets.UTF_8 );

      assertThrows( InvalidPluginException.class, () -> PluginArchive.extract( new ByteArrayInputStream( notAZip ), target ) );
   }
}
