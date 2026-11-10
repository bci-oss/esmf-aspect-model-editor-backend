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
package org.eclipse.esmf.ame.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.junit.jupiter.api.Test;

class JsonSchemasTest {
   private static final Map<String, Object> TOOL_SCHEMA = Map.of(
         "type", "object",
         "description", "Proposes a change",
         "properties", Map.of(
               "turtle", Map.of( "type", "string", "description", "The complete Turtle" ),
               "count", Map.of( "type", "integer" ),
               "score", Map.of( "type", "number" ),
               "fixable", Map.of( "type", "boolean" ),
               "severity", Map.of( "type", "string", "enum", List.of( "error", "warning", 3 ), "description", "Severity" ),
               "urns", Map.of( "type", "array", "items", Map.of( "type", "string" ) ),
               "target", Map.of( "type", "object", "properties", Map.of( "urn", Map.of( "type", "string" ) ), "required",
                     List.of( "urn" ) ) ),
         "required", List.of( "turtle", "severity" ) );

   @Test
   void convertsAnObjectSchemaWithAllPropertyTypes() {
      final JsonObjectSchema schema = JsonSchemas.toObjectSchema( TOOL_SCHEMA );
      final Map<String, JsonSchemaElement> properties = schema.properties();

      assertEquals( "Proposes a change", schema.description() );
      assertEquals( List.of( "turtle", "severity" ), schema.required() );
      assertEquals( 7, properties.size() );
      assertEquals( "The complete Turtle", assertInstanceOf( JsonStringSchema.class, properties.get( "turtle" ) ).description() );
      assertInstanceOf( JsonIntegerSchema.class, properties.get( "count" ) );
      assertInstanceOf( JsonNumberSchema.class, properties.get( "score" ) );
      assertInstanceOf( JsonBooleanSchema.class, properties.get( "fixable" ) );
      final JsonEnumSchema severity = assertInstanceOf( JsonEnumSchema.class, properties.get( "severity" ) );
      assertEquals( List.of( "error", "warning", "3" ), severity.enumValues() );
      assertEquals( "Severity", severity.description() );
      final JsonArraySchema urns = assertInstanceOf( JsonArraySchema.class, properties.get( "urns" ) );
      assertInstanceOf( JsonStringSchema.class, urns.items() );
      final JsonObjectSchema target = assertInstanceOf( JsonObjectSchema.class, properties.get( "target" ) );
      assertEquals( List.of( "urn" ), target.required() );
      assertInstanceOf( JsonStringSchema.class, target.properties().get( "urn" ) );
   }

   @Test
   void convertsAnEmptySchemaToAnEmptyObject() {
      final JsonObjectSchema schema = JsonSchemas.toObjectSchema( Map.of() );

      assertNull( schema.description() );
      assertTrue( schema.properties().isEmpty() );
      assertTrue( schema.required() == null || schema.required().isEmpty() );
   }

   @Test
   void treatsMissingAndUnknownTypesAsString() {
      assertInstanceOf( JsonStringSchema.class, JsonSchemas.toElement( Map.of() ) );
      assertInstanceOf( JsonStringSchema.class, JsonSchemas.toElement( Map.of( "type", "date" ) ) );
   }

   @Test
   void usesStringItemsForArraysWithoutItems() {
      final JsonArraySchema array = assertInstanceOf( JsonArraySchema.class,
            JsonSchemas.toElement( Map.of( "type", "array", "description", "URNs" ) ) );

      assertEquals( "URNs", array.description() );
      assertInstanceOf( JsonStringSchema.class, array.items() );
   }

   @Test
   void ignoresMalformedNestedSchemas() {
      final JsonObjectSchema schema = JsonSchemas.toObjectSchema( Map.of( "properties", Map.of( "broken", "not a schema" ),
            "required", "not a list" ) );

      assertInstanceOf( JsonStringSchema.class, schema.properties().get( "broken" ) );
      assertTrue( schema.required() == null || schema.required().isEmpty() );
      final JsonArraySchema array = assertInstanceOf( JsonArraySchema.class,
            JsonSchemas.toElement( Map.of( "type", "array", "items", List.of( "x" ) ) ) );
      assertInstanceOf( JsonStringSchema.class, array.items() );
   }
}
