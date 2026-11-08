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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.esmf.ame.services.models.plugins.InstalledPlugin;

import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginStorageTest {
   @TempDir
   Path tempDir;

   private PluginStorage storage;

   @BeforeEach
   void setUp() {
      storage = new PluginStorage( tempDir, JsonMapper.createDefault() );
   }

   @Test
   void readsEmptyStateWithoutStateFile() {
      assertTrue( storage.readState().isEmpty() );
   }

   @Test
   void writesAndReadsState() {
      storage.writeState( Map.of( "demo", new InstalledPlugin( "1.0.0", false ) ) );

      assertEquals( Map.of( "demo", new InstalledPlugin( "1.0.0", false ) ), storage.readState() );
   }

   @Test
   void updatesStateAndReturnsResultOfChange() {
      storage.writeState( Map.of( "demo", new InstalledPlugin( "1.0.0", false ) ) );

      final InstalledPlugin previous = storage.updateState( state -> state.put( "demo", new InstalledPlugin( "2.0.0", true ) ) );

      assertEquals( new InstalledPlugin( "1.0.0", false ), previous );
      assertEquals( Map.of( "demo", new InstalledPlugin( "2.0.0", true ) ), storage.readState() );
   }

   @Test
   void keepsStateWhenChangeFails() {
      storage.writeState( Map.of( "demo", new InstalledPlugin( "1.0.0", false ) ) );

      assertThrows( IllegalStateException.class, () -> storage.updateState( state -> {
         state.clear();
         throw new IllegalStateException( "rejected" );
      } ) );

      assertEquals( Map.of( "demo", new InstalledPlugin( "1.0.0", false ) ), storage.readState() );
   }

   @Test
   void removesLeftoversButKeepsInstalledVersionAndData() throws IOException {
      TestPluginArchives.installDirectly( storage, "demo", "2.0.0", true, TestPluginArchives.frontendPlugin( "demo", "2.0.0" ) );
      final Path outdated = Files.createDirectories( storage.versionDirectory( "demo", "1.0.0" ) );
      final Path data = storage.dataDirectory( "demo" );
      final Path removedPlugin = Files.createDirectories( storage.versionDirectory( "removed", "1.0.0" ) );
      final Path staging = storage.createStagingDirectory();

      storage.removeLeftovers();

      assertTrue( Files.isDirectory( storage.versionDirectory( "demo", "2.0.0" ) ) );
      assertTrue( Files.isDirectory( data ) );
      assertFalse( Files.exists( outdated ) );
      assertFalse( Files.exists( removedPlugin.getParent().getParent() ) );
      assertFalse( Files.exists( staging ) );
   }

   @Test
   void rejectsIdsLeavingThePluginsDirectory() {
      assertThrows( IllegalArgumentException.class, () -> storage.versionDirectory( "..", "1.0.0" ) );
   }
}
