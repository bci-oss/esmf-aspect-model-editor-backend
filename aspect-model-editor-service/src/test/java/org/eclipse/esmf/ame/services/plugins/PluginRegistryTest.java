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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.eclipse.esmf.ame.exceptions.PluginConflictException;
import org.eclipse.esmf.ame.exceptions.PluginExecutionException;
import org.eclipse.esmf.ame.plugin.api.AmePlugin;
import org.eclipse.esmf.ame.plugin.api.GenerationResult;
import org.eclipse.esmf.ame.plugin.api.GenerationSource;
import org.eclipse.esmf.ame.plugin.api.ImportResult;
import org.eclipse.esmf.ame.plugin.api.ImportSource;
import org.eclipse.esmf.ame.plugin.api.WorkspaceAccess;
import org.eclipse.esmf.ame.services.models.plugins.PluginState;
import org.eclipse.esmf.ame.services.plugins.testplugin.BrokenPlugin;
import org.eclipse.esmf.ame.services.plugins.testplugin.EchoPlugin;

import io.micronaut.http.HttpStatus;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginRegistryTest {
   @TempDir
   Path tempDir;

   private PluginStorage storage;
   private PluginRegistry registry;

   @BeforeEach
   void setUp() {
      storage = new PluginStorage( tempDir, JsonMapper.createDefault() );
      registry = new PluginRegistry( storage, new PluginManifestReader( JsonMapper.createDefault() ), mock( WorkspaceAccess.class ) );
   }

   @AfterEach
   void tearDown() {
      registry.stop();
   }

   @Test
   void startsEnabledBackendPlugin() throws IOException {
      install( "echo", EchoPlugin.class, true );

      registry.start();

      final LoadedPlugin plugin = registry.find( "echo" ).orElseThrow();
      assertEquals( PluginState.ACTIVE, plugin.state() );
      assertEquals( List.of( "context-class-loader", "crash", "echo", "reject", "visible-classes" ), plugin.actionIds() );
      assertEquals( List.of( "upper" ), plugin.importerIds() );
      assertEquals( "echo 1.0.0", Files.readString( storage.dataDirectory( "echo" ).resolve( "started" ) ) );
   }

   @Test
   void doesNotStartDisabledPlugins() {
      install( "echo", EchoPlugin.class, false );

      registry.start();

      assertTrue( registry.find( "echo" ).isEmpty() );
      assertThrows( PluginConflictException.class, () -> registry.runAction( "echo", "echo", "1" ) );
   }

   @Test
   void runsActionsWithThePluginAsContextClassLoader() {
      install( "echo", EchoPlugin.class, true );
      registry.start();

      assertEquals( "{\"a\":1}", registry.runAction( "echo", "echo", "{\"a\":1}" ) );
      assertEquals( "true", registry.runAction( "echo", "context-class-loader", "null" ) );
   }

   @Test
   void isolatesPluginsFromTheClassesOfTheEditor() {
      install( "echo", EchoPlugin.class, true );
      registry.start();

      final String visibility = registry.runAction( "echo", "visible-classes",
            "org.eclipse.esmf.ame.plugin.api.PluginApi,org.eclipse.esmf.ame.services.plugins.PluginService,io.micronaut.json.JsonMapper" );

      assertEquals( "org.eclipse.esmf.ame.plugin.api.PluginApi=true,org.eclipse.esmf.ame.services.plugins.PluginService=false,"
            + "io.micronaut.json.JsonMapper=false", visibility );
   }

   @Test
   void sharesTheLoggingApiOfTheEditorSoPluginsLogIntoItsLog() {
      install( "echo", EchoPlugin.class, true );
      registry.start();

      // The plugin JAR does not contain SLF4J, so the class can only come from the editor
      assertEquals( "org.slf4j.LoggerFactory=true", registry.runAction( "echo", "visible-classes", "org.slf4j.LoggerFactory" ) );
   }

   @Test
   void reportsRejectedAndFailedCallsDifferently() {
      install( "echo", EchoPlugin.class, true );
      registry.start();

      final PluginExecutionException rejected = assertThrows( PluginExecutionException.class,
            () -> registry.runAction( "echo", "reject", "x" ) );
      final PluginExecutionException failed = assertThrows( PluginExecutionException.class,
            () -> registry.runAction( "echo", "crash", "x" ) );

      assertEquals( "Rejected x", rejected.getMessage() );
      assertEquals( HttpStatus.UNPROCESSABLE_ENTITY.getCode(), rejected.getHttpStatusCode() );
      assertTrue( failed.getMessage().contains( "boom" ) );
      assertEquals( HttpStatus.INTERNAL_SERVER_ERROR.getCode(), failed.getHttpStatusCode() );
   }

   @Test
   void rejectsUnknownActionsImportersAndGenerators() {
      install( "echo", EchoPlugin.class, true );
      registry.start();

      assertThrows( NoSuchElementException.class, () -> registry.runAction( "echo", "unknown", "null" ) );
      assertThrows( NoSuchElementException.class,
            () -> registry.runImporter( "echo", "unknown", new ImportSource( "a.json", new byte[0], Map.of() ) ) );
      assertThrows( NoSuchElementException.class, () -> registry.runGenerator( "echo", "unknown", source( null ) ) );
      assertThrows( PluginConflictException.class, () -> registry.runGenerator( "missing", "summary", source( null ) ) );
   }

   @Test
   void runsGenerators() {
      install( "echo", EchoPlugin.class, true );
      registry.start();

      final GenerationResult result = registry.runGenerator( "echo", "summary", source( "urn:samm:org.example:1.0.0#speed" ) );

      assertEquals( "Movement.txt", result.files().getFirst().fileName() );
      assertEquals( "org.example:1.0.0 Movement.ttl urn:samm:org.example:1.0.0#speed {mode=strict}\n@prefix : <x> .",
            new String( result.files().getFirst().content(), StandardCharsets.UTF_8 ) );
      assertEquals( List.of( "generated" ), result.warnings() );
      assertEquals( List.of( "escape", "summary" ), registry.find( "echo" ).orElseThrow().generatorIds() );
   }

   @Test
   void runsImporters() {
      install( "echo", EchoPlugin.class, true );
      registry.start();

      final ImportResult result = registry.runImporter( "echo", "upper",
            new ImportSource( "movement.json", "speed".getBytes( StandardCharsets.UTF_8 ), Map.of( "mode", "strict" ) ) );

      assertEquals( "movement.ttl", result.models().getFirst().fileName() );
      assertEquals( "SPEED", result.models().getFirst().content() );
      assertEquals( List.of( "mode=strict" ), result.warnings() );
   }

   @Test
   void reportsPluginsWhichCannotStartWithoutAffectingOthers() {
      install( "broken", BrokenPlugin.class, true );
      install( "echo", EchoPlugin.class, true );

      registry.start();

      final LoadedPlugin broken = registry.find( "broken" ).orElseThrow();
      assertEquals( PluginState.FAILED, broken.state() );
      assertEquals( "Cannot start without configuration", broken.error() );
      assertNull( broken.runtime() );
      assertEquals( PluginState.ACTIVE, registry.find( "echo" ).orElseThrow().state() );
   }

   @Test
   void reportsPluginsWithInvalidFilesAsFailed() throws IOException {
      install( "echo", EchoPlugin.class, true );
      Files.delete( storage.versionDirectory( "echo", "1.0.0" ).resolve( PluginManifestReader.MANIFEST ) );

      registry.start();

      final LoadedPlugin plugin = registry.find( "echo" ).orElseThrow();
      assertEquals( PluginState.FAILED, plugin.state() );
      assertTrue( plugin.error().contains( "plugin.json" ) );
   }

   @Test
   void startsFrontendOnlyPlugins() {
      TestPluginArchives.installDirectly( storage, "ui", "1.0.0", true, TestPluginArchives.frontendPlugin( "ui", "1.0.0" ) );

      registry.start();

      final LoadedPlugin plugin = registry.find( "ui" ).orElseThrow();
      assertEquals( PluginState.ACTIVE, plugin.state() );
      assertNull( plugin.runtime() );
      assertTrue( plugin.actionIds().isEmpty() );
   }

   @Test
   void stopsPlugins() {
      install( "echo", EchoPlugin.class, true );
      registry.start();

      registry.stop();

      assertTrue( Files.exists( storage.dataDirectory( "echo" ).resolve( "stopped" ) ) );
      assertFalse( registry.find( "echo" ).isPresent() );
   }

   private static GenerationSource source( final String selectedElement ) {
      return new GenerationSource( "org.example", "1.0.0", "Movement.ttl", "@prefix : <x> .", selectedElement, Map.of( "mode", "strict" ) );
   }

   private void install( final String id, final Class<? extends AmePlugin> pluginClass,
         final boolean enabled ) {
      TestPluginArchives.installDirectly( storage, id, "1.0.0", enabled, TestPluginArchives.backendPlugin( id, "1.0.0", pluginClass ) );
   }
}
