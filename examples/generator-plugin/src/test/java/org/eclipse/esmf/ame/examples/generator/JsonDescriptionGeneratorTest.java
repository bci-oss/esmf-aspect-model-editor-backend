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

package org.eclipse.esmf.ame.examples.generator;

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

import org.eclipse.esmf.ame.plugin.api.GeneratedFile;
import org.eclipse.esmf.ame.plugin.api.GenerationResult;
import org.eclipse.esmf.ame.plugin.api.GenerationSource;
import org.eclipse.esmf.ame.plugin.api.PluginException;
import org.eclipse.esmf.aspectmodel.loader.AspectModelLoader;
import org.eclipse.esmf.aspectmodel.validation.services.AspectModelValidator;
import org.eclipse.esmf.metamodel.AspectModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonDescriptionGeneratorTest {
   private static final ObjectMapper MAPPER = new ObjectMapper();
   private static final Path SAMPLES = Path.of( "samples" );
   private static final String MOVEMENT_MODEL = """
         @prefix samm: <urn:samm:org.eclipse.esmf.samm:meta-model:2.2.0#> .
         @prefix samm-c: <urn:samm:org.eclipse.esmf.samm:characteristic:2.2.0#> .
         @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
         @prefix : <urn:samm:org.example.movement:1.2.0#> .

         :Movement a samm:Aspect ;
            samm:description "Describes the \\"movement\\" of a vehicle."@en ;
            samm:properties ( :speed :moving :driver :timestamp ) ;
            samm:operations ( ) .
         :speed a samm:Property ; samm:description "The current speed."@en ; samm:characteristic :Speed .
         :Speed a samm:Characteristic ; samm:dataType xsd:float .
         :moving a samm:Property ; samm:characteristic samm-c:Boolean .
         :driver a samm:Property ; samm:characteristic samm-c:Text .
         :timestamp a samm:Property ; samm:characteristic samm-c:Timestamp .
         """;
   private static final String MOVEMENT_DESCRIPTION = """
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
               { "name": "timestamp", "type": "dateTime" }
             ]
           }
         }
         """;
   private static final String MIXED_MODEL = """
         @prefix samm: <urn:samm:org.eclipse.esmf.samm:meta-model:2.2.0#> .
         @prefix samm-c: <urn:samm:org.eclipse.esmf.samm:characteristic:2.2.0#> .
         @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
         @prefix : <urn:samm:org.example:1.0.0#> .

         :Vehicle a samm:Aspect ;
            samm:description "Ein Fahrzeug"@de, "A vehicle"@en ;
            samm:properties ( [ samm:property :name ; samm:optional true ] :position :count ) ;
            samm:operations ( ) .
         :name a samm:Property ; samm:characteristic samm-c:Text .
         :position a samm:Property ; samm:characteristic samm-c:MultiLanguageText .
         :count a samm:Property ; samm:characteristic :Count .
         :Count a samm:Characteristic ; samm:dataType xsd:int .
         """;

   private final JsonDescriptionGenerator generator = new JsonDescriptionGenerator();

   @Test
   void sampleModelIsAValidAspectModel() throws IOException {
      final AspectModel aspectModel = new AspectModelLoader().load(
            new ByteArrayInputStream( Files.readAllBytes( SAMPLES.resolve( "Movement.ttl" ) ) ), URI.create( "inmemory:Movement.ttl" ) );

      assertEquals( List.of(), new AspectModelValidator().validateModel( aspectModel ).violations() );
   }

   @Test
   void generatesTheSampleOutputFromTheSampleModel() throws Exception {
      final String model = Files.readString( SAMPLES.resolve( "Movement.ttl" ) );

      final GenerationResult result = generator.generate(
            new GenerationSource( "org.example.movement", "1.0.0", "Movement.ttl", model, null, Map.of() ) );

      final GeneratedFile file = result.files().getFirst();
      assertEquals( "Movement.description.json", file.fileName() );
      assertEquals( Files.readString( SAMPLES.resolve( "Movement.description.json" ) ).strip(),
            new String( file.content(), StandardCharsets.UTF_8 ).strip() );
      assertEquals( List.of(), result.warnings() );
   }

   @Test
   void generatesTheJsonDescription() throws Exception {
      final GenerationResult result = generator.generate( source( "org.example.movement", "1.2.0", MOVEMENT_MODEL, null, Map.of() ) );

      final GeneratedFile file = result.files().getFirst();
      assertEquals( "Movement.description.json", file.fileName() );
      assertEquals( "application/json", file.mediaType() );
      assertEquals( MAPPER.readTree( MOVEMENT_DESCRIPTION ), MAPPER.readTree( file.content() ) );
      assertEquals( List.of(), result.warnings() );
   }

   @Test
   void skipsUnsupportedTypesAndReadsOptionalProperties() throws Exception {
      final GenerationResult result = generator.generate( source( "org.example", "1.0.0", MIXED_MODEL, null, Map.of() ) );

      final JsonNode aspect = MAPPER.readTree( result.files().getFirst().content() ).path( "aspect" );
      assertEquals( "A vehicle", aspect.path( "description" ).asText() );
      assertEquals( "[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"count\",\"type\":\"int\"}]",
            aspect.path( "properties" ).toString() );
      assertEquals( 1, result.warnings().size() );
      assertTrue( result.warnings().getFirst().contains( "'position'" ) );
   }

   @Test
   void exportsOnlyTheSelectedPropertyWithoutDescriptions() throws Exception {
      final GenerationResult result = generator.generate( source( "org.example", "1.0.0", MIXED_MODEL,
            "urn:samm:org.example:1.0.0#count", Map.of( JsonDescriptionGenerator.DESCRIPTIONS_OPTION, "false" ) ) );

      final JsonNode aspect = MAPPER.readTree( result.files().getFirst().content() ).path( "aspect" );
      assertTrue( aspect.path( "description" ).isMissingNode() );
      assertEquals( "[{\"name\":\"count\",\"type\":\"int\"}]", aspect.path( "properties" ).toString() );
      assertEquals( List.of(), result.warnings() );
   }

   @Test
   void exportsAllPropertiesIfTheSelectedElementIsNoPropertyOfTheAspect() throws Exception {
      final GenerationResult result = generator.generate( source( "org.example", "1.0.0", MIXED_MODEL,
            "urn:samm:org.example:1.0.0#Vehicle", Map.of() ) );

      assertEquals( 2, MAPPER.readTree( result.files().getFirst().content() ).path( "aspect" ).path( "properties" ).size() );
   }

   @Test
   void rejectsModelsWithoutAspect() {
      final String model = MIXED_MODEL.replace( ":Vehicle a samm:Aspect", ":Vehicle a samm:Entity" );

      final PluginException exception = assertThrows( PluginException.class,
            () -> generator.generate( source( "org.example", "1.0.0", model, null, Map.of() ) ) );

      assertTrue( exception.getMessage().contains( "exactly one Aspect" ), exception.getMessage() );
   }

   @Test
   void rejectsInvalidTurtle() {
      final PluginException exception = assertThrows( PluginException.class,
            () -> generator.generate( source( "org.example", "1.0.0", ":broken", null, Map.of() ) ) );

      assertTrue( exception.getMessage().startsWith( "The Aspect Model could not be read" ), exception.getMessage() );
   }

   private static GenerationSource source( final String namespace, final String version, final String content, final String selected,
         final Map<String, String> options ) {
      return new GenerationSource( namespace, version, "Model.ttl", content, selected, options );
   }
}
