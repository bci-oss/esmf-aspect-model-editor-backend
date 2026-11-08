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
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.exceptions.InvalidPluginException;
import org.eclipse.esmf.ame.exceptions.PluginConflictException;
import org.eclipse.esmf.ame.exceptions.PluginExecutionException;
import org.eclipse.esmf.ame.plugin.api.WorkspaceAccess;
import org.eclipse.esmf.ame.services.models.plugins.PluginGenerationRequest;
import org.eclipse.esmf.ame.services.models.plugins.PluginGenerationResult;
import org.eclipse.esmf.ame.services.models.plugins.PluginImportResult;
import org.eclipse.esmf.ame.services.models.plugins.PluginManifest.CommandContribution;
import org.eclipse.esmf.ame.services.models.plugins.PluginModel;
import org.eclipse.esmf.ame.services.models.plugins.PluginState;
import org.eclipse.esmf.ame.services.models.plugins.PluginStatus;
import org.eclipse.esmf.ame.services.models.plugins.PluginsOverview;
import org.eclipse.esmf.ame.services.plugins.testplugin.EchoPlugin;

import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginServiceTest {
   @TempDir
   Path tempDir;

   private PluginStorage storage;
   private PluginRegistry registry;
   private PluginService service;

   @BeforeEach
   void setUp() {
      storage = new PluginStorage( tempDir, JsonMapper.createDefault() );
      final PluginManifestReader manifestReader = new PluginManifestReader( JsonMapper.createDefault() );
      registry = new PluginRegistry( storage, manifestReader, mock( WorkspaceAccess.class ) );
      service = new PluginService( storage, manifestReader, registry );
   }

   @AfterEach
   void tearDown() {
      registry.stop();
   }

   @Test
   void installedPluginIsEnabledAndStartsAfterRestart() {
      registry.start();

      final PluginStatus status = install( TestPluginArchives.backendPlugin( "echo", "1.0.0", EchoPlugin.class ) );

      assertEquals( "Plugin echo", status.name() );
      assertEquals( PluginState.INACTIVE, status.state() );
      assertTrue( status.enabled() );
      assertTrue( status.restartRequired() );
      assertTrue( service.getOverview().restartRequired() );
      assertTrue( Files.isRegularFile( storage.versionDirectory( "echo", "1.0.0" ).resolve( "backend/plugin.jar" ) ) );

      restart();

      final PluginStatus running = service.getOverview().plugins().getFirst();
      assertEquals( PluginState.ACTIVE, running.state() );
      assertFalse( running.restartRequired() );
      assertEquals( List.of( "open", "summary" ), running.commands().stream().map( CommandContribution::id ).toList() );
      assertTrue( running.actions().contains( "echo" ) );
      assertEquals( List.of( "escape", "summary" ), running.generators() );
      assertFalse( service.getOverview().restartRequired() );
   }

   @Test
   void rejectsInvalidArchivesAndCleansUp() throws IOException {
      registry.start();
      final byte[] archive = TestPluginArchives.zip( Map.of( "readme.txt", "no plugin".getBytes( StandardCharsets.UTF_8 ) ) );

      assertThrows( InvalidPluginException.class, () -> install( archive ) );

      try ( final Stream<Path> files = Files.list( tempDir ) ) {
         assertTrue( files.noneMatch( file -> file.getFileName().toString().startsWith( ".staging-" ) ) );
      }
      assertTrue( service.getOverview().plugins().isEmpty() );
   }

   @Test
   void refusesToReplaceTheRunningVersion() {
      install( TestPluginArchives.backendPlugin( "echo", "1.0.0", EchoPlugin.class ) );
      restart();

      assertThrows( PluginConflictException.class, () -> install( TestPluginArchives.backendPlugin( "echo", "1.0.0", EchoPlugin.class ) ) );

      final PluginStatus updated = install( TestPluginArchives.backendPlugin( "echo", "1.1.0", EchoPlugin.class ) );
      assertEquals( "1.1.0", updated.version() );
      assertEquals( PluginState.ACTIVE, updated.state() );
      assertTrue( updated.restartRequired() );
   }

   @Test
   void disablingARunningPluginRequiresRestart() {
      install( TestPluginArchives.backendPlugin( "echo", "1.0.0", EchoPlugin.class ) );
      restart();

      final PluginStatus disabled = service.setEnabled( "echo", false );

      assertFalse( disabled.enabled() );
      assertEquals( PluginState.ACTIVE, disabled.state() );
      assertTrue( disabled.restartRequired() );

      restart();

      final PluginStatus stopped = service.getOverview().plugins().getFirst();
      assertEquals( PluginState.INACTIVE, stopped.state() );
      assertFalse( stopped.restartRequired() );
   }

   @Test
   void removesStoppedPluginsImmediately() {
      registry.start();
      install( TestPluginArchives.frontendPlugin( "ui", "1.0.0" ) );

      service.uninstall( "ui" );

      assertFalse( Files.exists( storage.versionDirectory( "ui", "1.0.0" ) ) );
      assertTrue( service.getOverview().plugins().isEmpty() );
      assertFalse( service.getOverview().restartRequired() );
   }

   @Test
   void removesRunningPluginsAfterRestart() {
      install( TestPluginArchives.backendPlugin( "echo", "1.0.0", EchoPlugin.class ) );
      restart();

      service.uninstall( "echo" );

      final PluginsOverview overview = service.getOverview();
      assertTrue( overview.plugins().isEmpty() );
      assertTrue( overview.restartRequired() );
      assertTrue( Files.exists( storage.versionDirectory( "echo", "1.0.0" ) ) );

      restart();

      assertFalse( Files.exists( storage.versionDirectory( "echo", "1.0.0" ) ) );
      assertFalse( service.getOverview().restartRequired() );
   }

   @Test
   void rejectsUnknownPlugins() {
      registry.start();

      assertThrows( NoSuchElementException.class, () -> service.uninstall( "unknown" ) );
      assertThrows( NoSuchElementException.class, () -> service.setEnabled( "unknown", true ) );
   }

   @Test
   void servesFrontendFilesOfRunningPluginsOnly() {
      install( TestPluginArchives.frontendPlugin( "ui", "1.0.0" ) );
      assertThrows( NoSuchElementException.class, () -> service.frontendFile( "ui", "main.js" ) );

      restart();

      assertTrue( service.frontendFile( "ui", "main.js" ).endsWith( Path.of( "frontend", "main.js" ) ) );
      assertThrows( NoSuchElementException.class, () -> service.frontendFile( "ui", "../plugin.json" ) );
      assertThrows( NoSuchElementException.class, () -> service.frontendFile( "ui", "missing.js" ) );
   }

   @Test
   void importsWithPluginImporters() {
      install( TestPluginArchives.backendPlugin( "echo", "1.0.0", EchoPlugin.class ) );
      restart();

      final PluginImportResult result = service.importModel( "echo", "upper", "movement.json", "speed".getBytes( StandardCharsets.UTF_8 ),
            Map.of() );

      assertEquals( new PluginModel( "org.example", "1.0.0", "movement.ttl", "SPEED" ), result.models().getFirst() );
   }

   @Test
   void generatesWithPluginGenerators() {
      install( TestPluginArchives.backendPlugin( "echo", "1.0.0", EchoPlugin.class ) );
      restart();
      final PluginModel model = new PluginModel( "org.example", "1.0.0", "Movement.ttl", "TTL" );

      final PluginGenerationResult result = service.generate( "echo", "summary", new PluginGenerationRequest( model, null, null ) );

      final PluginGenerationResult.File file = result.files().getFirst();
      assertEquals( "Movement.txt", file.fileName() );
      assertEquals( "text/plain", file.mediaType() );
      assertEquals( "org.example:1.0.0 Movement.ttl - {}\nTTL",
            new String( Base64.getDecoder().decode( file.content() ), StandardCharsets.UTF_8 ) );
      assertEquals( List.of( "generated" ), result.warnings() );
   }

   @Test
   void rejectsGenerationWithoutModelAndFilesOutsideTheTarget() {
      install( TestPluginArchives.backendPlugin( "echo", "1.0.0", EchoPlugin.class ) );
      restart();
      final PluginModel model = new PluginModel( "org.example", "1.0.0", "Movement.ttl", "TTL" );

      assertThrows( IllegalArgumentException.class,
            () -> service.generate( "echo", "summary", new PluginGenerationRequest( null, null, null ) ) );
      final PluginExecutionException escape = assertThrows( PluginExecutionException.class,
            () -> service.generate( "echo", "escape", new PluginGenerationRequest( model, null, Map.of() ) ) );
      assertTrue( escape.getMessage().contains( "invalid name '../outside.txt'" ), escape.getMessage() );
   }

   private PluginStatus install( final byte[] archive ) {
      return service.install( new ByteArrayInputStream( archive ) );
   }

   private void restart() {
      registry.stop();
      registry.start();
      service = new PluginService( storage, new PluginManifestReader( JsonMapper.createDefault() ), registry );
   }
}
