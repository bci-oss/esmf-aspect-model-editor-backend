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

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

/**
 * Converts the JSON schemas of the tools (plain maps) to LangChain4j schemas.
 */
final class JsonSchemas {
   private JsonSchemas() {
   }

   static JsonObjectSchema toObjectSchema( final Map<String, Object> schema ) {
      final JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
      final String description = string( schema.get( "description" ) );
      if ( description != null ) {
         builder.description( description );
      }
      final Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
      if ( schema.get( "properties" ) instanceof final Map<?, ?> props ) {
         props.forEach( ( name, value ) -> properties.put( String.valueOf( name ), toElement( map( value ) ) ) );
      }
      builder.addProperties( properties );
      if ( schema.get( "required" ) instanceof final List<?> required ) {
         builder.required( required.stream().map( String::valueOf ).toList() );
      }
      return builder.build();
   }

   static JsonSchemaElement toElement( final Map<String, Object> schema ) {
      final String description = string( schema.get( "description" ) );
      if ( schema.get( "enum" ) instanceof final List<?> values ) {
         final List<String> enumValues = new ArrayList<>();
         values.forEach( value -> enumValues.add( String.valueOf( value ) ) );
         return JsonEnumSchema.builder().description( description ).enumValues( enumValues ).build();
      }
      final String type = string( schema.get( "type" ) );
      return switch ( type == null ? "string" : type ) {
         case "object" -> toObjectSchema( schema );
         case "array" -> JsonArraySchema.builder().description( description )
               .items( toElement( map( schema.getOrDefault( "items", Map.of( "type", "string" ) ) ) ) ).build();
         case "integer" -> JsonIntegerSchema.builder().description( description ).build();
         case "number" -> JsonNumberSchema.builder().description( description ).build();
         case "boolean" -> JsonBooleanSchema.builder().description( description ).build();
         default -> JsonStringSchema.builder().description( description ).build();
      };
   }

   @SuppressWarnings( "unchecked" )
   private static Map<String, Object> map( final Object value ) {
      return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
   }

   private static String string( final Object value ) {
      return value == null ? null : String.valueOf( value );
   }
}
