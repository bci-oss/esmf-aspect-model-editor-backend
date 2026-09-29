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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.esmf.ame.exceptions.FileNotFoundException;
import org.eclipse.esmf.ame.services.models.workspace.WorkspaceConfig;

import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceConfigServiceTest {

   @TempDir
   Path tempDir;

   private Path configFilePath;
   private Path defaultWorkspacePath;
   private JsonMapper jsonMapper;
   private WorkspaceConfigService service;

   @BeforeEach
   void setUp() {
      configFilePath = tempDir.resolve( "workspaces.json" );
      defaultWorkspacePath = tempDir.resolve( "default-workspace" );
      jsonMapper = JsonMapper.createDefault();
      service = new WorkspaceConfigService( configFilePath, defaultWorkspacePath, jsonMapper );
   }

   @Test
   void testInitialConfigCreatesFileAndDefaultWorkspace() {
      assertTrue( Files.exists( configFilePath ) );
      assertTrue( Files.exists( defaultWorkspacePath ) );

      final WorkspaceConfig config = service.getConfig();
      assertNotNull( config );
      assertEquals( 1, config.workspaces().size() );
      assertEquals( defaultWorkspacePath.toAbsolutePath().normalize().toString(), config.activeWorkspace() );
      assertEquals( defaultWorkspacePath.toAbsolutePath().normalize(), service.getActiveWorkspacePath() );
      assertEquals( List.of( defaultWorkspacePath.toAbsolutePath().normalize() ), service.getWorkspacePaths() );
   }

   @Test
   void testAddWorkspaceWithoutActive() {
      final Path newWorkspace = tempDir.resolve( "workspace-two" );
      final WorkspaceConfig updated = service.addWorkspace( newWorkspace.toString(), false );

      assertEquals( 2, updated.workspaces().size() );
      assertTrue( updated.workspaces().contains( newWorkspace.toAbsolutePath().normalize().toString() ) );
      assertEquals( defaultWorkspacePath.toAbsolutePath().normalize().toString(), updated.activeWorkspace() );
      assertTrue( Files.exists( newWorkspace ) );
   }

   @Test
   void testAddWorkspaceWithActive() {
      final Path newWorkspace = tempDir.resolve( "workspace-active" );
      final WorkspaceConfig updated = service.addWorkspace( newWorkspace.toString(), true );

      assertEquals( 2, updated.workspaces().size() );
      assertEquals( newWorkspace.toAbsolutePath().normalize().toString(), updated.activeWorkspace() );
      assertEquals( newWorkspace.toAbsolutePath().normalize(), service.getActiveWorkspacePath() );
   }

   @Test
   void testAddDuplicateWorkspaceDoesNotDuplicate() {
      final Path newWorkspace = tempDir.resolve( "workspace-dup" );
      service.addWorkspace( newWorkspace.toString(), false );
      final WorkspaceConfig updated = service.addWorkspace( newWorkspace.toString(), false );

      assertEquals( 2, updated.workspaces().size() );
   }

   @Test
   void testSetActiveWorkspaceSuccess() {
      final Path newWorkspace = tempDir.resolve( "workspace-to-activate" );
      service.addWorkspace( newWorkspace.toString(), false );

      final WorkspaceConfig updated = service.setActiveWorkspace( newWorkspace.toString() );
      assertEquals( newWorkspace.toAbsolutePath().normalize().toString(), updated.activeWorkspace() );
      assertEquals( newWorkspace.toAbsolutePath().normalize(), service.getActiveWorkspacePath() );
   }

   @Test
   void testSetActiveWorkspaceFailsWhenNotRegistered() {
      final Path unregistered = tempDir.resolve( "unregistered" );
      assertThrows( IllegalArgumentException.class, () -> service.setActiveWorkspace( unregistered.toString() ) );
   }

   @Test
   void testRemoveWorkspaceSuccess() {
      final Path ws2 = tempDir.resolve( "workspace-2" );
      service.addWorkspace( ws2.toString(), false );
      assertEquals( 2, service.getConfig().workspaces().size() );

      final WorkspaceConfig updated = service.removeWorkspace( ws2.toString() );
      assertEquals( 1, updated.workspaces().size() );
      assertFalse( updated.workspaces().contains( ws2.toAbsolutePath().normalize().toString() ) );
   }

   @Test
   void testRemoveActiveWorkspaceSwitchesToRemaining() {
      final Path ws2 = tempDir.resolve( "workspace-2" );
      service.addWorkspace( ws2.toString(), true );
      assertEquals( ws2.toAbsolutePath().normalize().toString(), service.getConfig().activeWorkspace() );

      final WorkspaceConfig updated = service.removeWorkspace( ws2.toString() );
      assertEquals( 1, updated.workspaces().size() );
      assertEquals( defaultWorkspacePath.toAbsolutePath().normalize().toString(), updated.activeWorkspace() );
   }

   @Test
   void testRemoveDefaultWorkspaceThrowsIllegalArgument() {
      assertThrows( IllegalArgumentException.class, () -> service.removeWorkspace( defaultWorkspacePath.toString() ) );
   }

   @Test
   void testRemoveNonExistentWorkspaceThrowsFileNotFound() {
      final Path nonExistent = tempDir.resolve( "non-existent" );
      assertThrows( FileNotFoundException.class, () -> service.removeWorkspace( nonExistent.toString() ) );
   }

   @Test
   void testResetToDefault() {
      final Path ws2 = tempDir.resolve( "workspace-2" );
      service.addWorkspace( ws2.toString(), true );
      assertEquals( 2, service.getConfig().workspaces().size() );

      final WorkspaceConfig reset = service.resetToDefault();
      assertEquals( 1, reset.workspaces().size() );
      assertEquals( defaultWorkspacePath.toAbsolutePath().normalize().toString(), reset.activeWorkspace() );
   }

   @Test
   void testPersistenceAcrossInstances() {
      final Path ws2 = tempDir.resolve( "persisted-ws" );
      service.addWorkspace( ws2.toString(), true );

      // Instantiate a second service pointing to the same config file
      final WorkspaceConfigService service2 = new WorkspaceConfigService( configFilePath, defaultWorkspacePath, jsonMapper );
      final WorkspaceConfig loaded = service2.getConfig();

      assertEquals( 2, loaded.workspaces().size() );
      assertEquals( ws2.toAbsolutePath().normalize().toString(), loaded.activeWorkspace() );
   }

   @Test
   void testLoadConfigEnsuresDefaultWorkspaceIsAlwaysIncluded() throws IOException {
      // Simulate external file that omitted default workspace
      final Path customWs = tempDir.resolve( "only-custom" );
      Files.createDirectories( customWs );
      final String json = String.format( "{\"workspaces\":[\"%s\"],\"activeWorkspace\":\"%s\"}",
            customWs.toAbsolutePath().normalize().toString().replace( "\\", "\\\\" ),
            customWs.toAbsolutePath().normalize().toString().replace( "\\", "\\\\" ) );
      Files.writeString( configFilePath, json );

      final WorkspaceConfigService service2 = new WorkspaceConfigService( configFilePath, defaultWorkspacePath, jsonMapper );
      final WorkspaceConfig loaded = service2.getConfig();

      assertTrue( loaded.workspaces().contains( defaultWorkspacePath.toAbsolutePath().normalize().toString() ) );
      assertTrue( loaded.workspaces().contains( customWs.toAbsolutePath().normalize().toString() ) );
   }
}
