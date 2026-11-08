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

package org.eclipse.esmf.ame.examples.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.esmf.ame.plugin.api.ImportResult;
import org.eclipse.esmf.ame.plugin.api.ImportSource;
import org.eclipse.esmf.ame.plugin.api.ImportedModel;
import org.eclipse.esmf.ame.plugin.api.PluginException;
import org.eclipse.esmf.aspectmodel.loader.AspectModelLoader;
import org.eclipse.esmf.aspectmodel.validation.services.AspectModelValidator;
import org.eclipse.esmf.metamodel.AspectModel;
import org.eclipse.esmf.metamodel.Property;

import org.junit.jupiter.api.Test;

class JsonDescriptionImporterTest {
   private static final String MOVEMENT = """
         {
           "namespace": "org.example.movement",
           "version": "1.2.0",
           "aspect": {
             "name": "Movement",
             "description": "Describes the \\"movement\\" of a vehicle.",
             "properties": [
               { "name": "speed", "type": "float", "description": "The current speed." },
               { "name": "moving", "type": "boolean" },
               { "name": "driver", "type": "string" },
               { "name": "timestamp", "type": "dateTime" },
               { "name": "Invalid", "type": "string" },
               { "name": "position", "type": "geometry" }
             ]
           }
         }
         """;

   private final JsonDescriptionImporter importer = new JsonDescriptionImporter();

   @Test
   void importCreatesValidAspectModel() throws PluginException {
      final ImportResult result = importer.importModel( source( MOVEMENT, Map.of() ) );

      assertEquals( 1, result.models().size() );
      final ImportedModel model = result.models().getFirst();
      assertEquals( "org.example.movement", model.namespace() );
      assertEquals( "1.2.0", model.version() );
      assertEquals( "Movement.ttl", model.fileName() );

      final AspectModel aspectModel = loadValid( model );
      assertEquals( List.of( "speed", "moving", "driver", "timestamp" ),
            aspectModel.aspect().getProperties().stream().map( Property::getName ).toList() );
   }

   @Test
   void importsTheSample() throws IOException, PluginException {
      final byte[] sample = Files.readAllBytes( Path.of( "samples", "movement.json" ) );

      final ImportResult result = importer.importModel( new ImportSource( "movement.json", sample, Map.of() ) );

      assertEquals( List.of(), result.warnings() );
      final AspectModel aspectModel = loadValid( result.models().getFirst() );
      assertEquals( "Movement", aspectModel.aspect().getName() );
      assertEquals( List.of( "speed", "moving", "timestamp" ),
            aspectModel.aspect().getProperties().stream().map( Property::getName ).toList() );
   }

   @Test
   void importReportsSkippedPropertiesAsWarnings() throws PluginException {
      final List<String> warnings = importer.importModel( source( MOVEMENT, Map.of() ) ).warnings();

      assertEquals( 2, warnings.size() );
      assertTrue( warnings.get( 0 ).contains( "'Invalid'" ) );
      assertTrue( warnings.get( 1 ).contains( "'geometry'" ) );
   }

   @Test
   void namespaceOptionOverridesNamespaceOfFile() throws PluginException {
      final ImportedModel model = importer.importModel(
            source( MOVEMENT, Map.of( JsonDescriptionImporter.NAMESPACE_OPTION, "com.example.override" ) ) ).models().getFirst();

      assertEquals( "com.example.override", model.namespace() );
      assertTrue( model.content().contains( "<urn:samm:com.example.override:1.2.0#>" ) );
   }

   @Test
   void importRejectsInvalidJson() {
      final PluginException exception = assertThrows( PluginException.class,
            () -> importer.importModel( source( "{ no json", Map.of() ) ) );

      assertTrue( exception.getMessage().startsWith( "The file is not valid JSON" ) );
   }

   @Test
   void importRejectsMissingAspectName() {
      final PluginException exception = assertThrows( PluginException.class,
            () -> importer.importModel( source( "{ \"namespace\": \"org.example\" }", Map.of() ) ) );

      assertTrue( exception.getMessage().contains( "The aspect needs a name" ) );
   }

   @Test
   void importRejectsInvalidNamespace() {
      final PluginException exception = assertThrows( PluginException.class,
            () -> importer.importModel( source( MOVEMENT, Map.of( JsonDescriptionImporter.NAMESPACE_OPTION, "1.invalid" ) ) ) );

      assertTrue( exception.getMessage().contains( "'1.invalid' is invalid" ) );
   }

   private static AspectModel loadValid( final ImportedModel model ) {
      final AspectModel aspectModel = new AspectModelLoader().load(
            new ByteArrayInputStream( model.content().getBytes( StandardCharsets.UTF_8 ) ),
            URI.create( "inmemory:" + model.fileName() ) );
      assertEquals( List.of(), new AspectModelValidator().validateModel( aspectModel ).violations() );
      return aspectModel;
   }

   private static ImportSource source( final String json, final Map<String, String> options ) {
      return new ImportSource( "movement.json", json.getBytes( StandardCharsets.UTF_8 ), options );
   }
}
