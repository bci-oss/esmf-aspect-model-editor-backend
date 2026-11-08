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

package org.eclipse.esmf.ame.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.esmf.ame.exceptions.InvalidPluginException;
import org.eclipse.esmf.ame.model.MockFileUpload;
import org.eclipse.esmf.ame.services.models.plugins.PluginGenerationRequest;
import org.eclipse.esmf.ame.services.models.plugins.PluginGenerationResult;
import org.eclipse.esmf.ame.services.models.plugins.PluginImportResult;
import org.eclipse.esmf.ame.services.models.plugins.PluginModel;
import org.eclipse.esmf.ame.services.models.plugins.PluginStatus;
import org.eclipse.esmf.ame.services.models.plugins.PluginsOverview;
import org.eclipse.esmf.ame.services.plugins.PluginService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginControllerTest {
   private static final byte[] CONTENT = "content".getBytes( StandardCharsets.UTF_8 );

   private PluginService pluginService;
   private PluginController controller;

   @BeforeEach
   void setUp() {
      pluginService = mock( PluginService.class );
      controller = new PluginController( pluginService, JsonMapper.createDefault() );
   }

   @Test
   void installsZipArchives() {
      final PluginStatus status = mock( PluginStatus.class );
      when( pluginService.install( any() ) ).thenReturn( status );

      final HttpResponse<PluginStatus> response = controller.installPlugin(
            MockFileUpload.create( "plugin.ZIP", CONTENT, MediaType.APPLICATION_OCTET_STREAM_TYPE ) );

      assertEquals( HttpStatus.CREATED, response.getStatus() );
      assertEquals( status, response.body() );
   }

   @Test
   void rejectsFilesWhichAreNoZipArchives() {
      assertThrows( InvalidPluginException.class,
            () -> controller.installPlugin( MockFileUpload.create( "plugin.jar", CONTENT, MediaType.APPLICATION_OCTET_STREAM_TYPE ) ) );
      verify( pluginService, never() ).install( any() );
   }

   @Test
   void returnsTheOverviewAfterUninstalling() {
      final PluginsOverview overview = new PluginsOverview( 1, List.of(), true );
      when( pluginService.getOverview() ).thenReturn( overview );

      assertEquals( overview, controller.uninstallPlugin( "echo" ).body() );
      verify( pluginService ).uninstall( "echo" );
   }

   @Test
   void passesNullForEmptyActionPayloads() {
      when( pluginService.runAction( "echo", "echo", "null" ) ).thenReturn( "null" );
      when( pluginService.runAction( "echo", "echo", "{\"a\":1}" ) ).thenReturn( "{\"a\":1}" );

      final HttpResponse<String> empty = controller.runAction( "echo", "echo", " " );
      final HttpResponse<String> payload = controller.runAction( "echo", "echo", "{\"a\":1}" );

      assertEquals( "null", empty.body() );
      assertEquals( MediaType.APPLICATION_JSON_TYPE, empty.getContentType().orElseThrow() );
      assertEquals( "{\"a\":1}", payload.body() );
   }

   @Test
   void passesImportOptionsToTheImporter() {
      final PluginImportResult result = new PluginImportResult( List.of(), List.of() );
      when( pluginService.importModel( "example-import", "json", "model.json", CONTENT, Map.of( "mode", "strict" ) ) ).thenReturn( result );

      final HttpResponse<PluginImportResult> response = controller.importModel( "example-import", "json",
            MockFileUpload.create( "model.json", CONTENT, MediaType.APPLICATION_JSON_TYPE ), "{\"mode\":\"strict\"}" );

      assertEquals( result, response.body() );
   }

   @Test
   void rejectsInvalidImportOptions() {
      assertThrows( IllegalArgumentException.class, () -> controller.importModel( "example-import", "json",
            MockFileUpload.create( "model.json", CONTENT, MediaType.APPLICATION_JSON_TYPE ), "[1]" ) );
   }

   @Test
   void passesTheGenerationRequestToTheGenerator() throws IOException {
      final PluginGenerationRequest request = JsonMapper.createDefault().readValue( """
            { "model": { "namespace": "org.example", "version": "1.0.0", "fileName": "Movement.ttl", "content": "TTL" },
              "selectedElement": "urn:samm:org.example:1.0.0#speed", "options": { "mode": "strict" } }
            """, PluginGenerationRequest.class );
      final PluginGenerationResult result = new PluginGenerationResult(
            List.of( new PluginGenerationResult.File( "Movement.json", "application/json", "e30=" ) ), List.of() );
      when( pluginService.generate( "example-generator", "json", request ) ).thenReturn( result );

      final HttpResponse<PluginGenerationResult> response = controller.generate( "example-generator", "json", request );

      assertEquals( result, response.body() );
      assertEquals( new PluginModel( "org.example", "1.0.0", "Movement.ttl", "TTL" ), request.model() );
      assertEquals( Map.of( "mode", "strict" ), request.options() );
   }

   @Test
   void servesJavaScriptModulesWithTheirMediaType() {
      when( pluginService.frontendFile( "ui", "main.js" ) ).thenReturn( Path.of( "plugins", "ui", "main.js" ) );
      when( pluginService.frontendFile( "ui", "style.css" ) ).thenReturn( Path.of( "plugins", "ui", "style.css" ) );

      final SystemFile script = controller.getFrontendFile( "ui", "main.js" ).body();
      final SystemFile style = controller.getFrontendFile( "ui", "style.css" ).body();

      assertEquals( "text/javascript", script.getMediaType().toString() );
      assertEquals( "text/css", style.getMediaType().toString() );
   }
}
