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
package org.eclipse.esmf.ame.ai.tools;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.Nullable;

/**
 * Reads the JSON arguments of a tool call. Missing required arguments are reported as
 * {@link IllegalArgumentException}, which is returned to the AI.
 */
final class ToolArguments {
   private ToolArguments() {
   }

   /**
    * @param argumentsJson the arguments of the tool call; blank means no arguments
    * @return the parsed arguments
    * @throws IOException if the arguments are not valid JSON
    */
   static JsonNode parse( final ObjectMapper objectMapper, final @Nullable String argumentsJson ) throws IOException {
      return argumentsJson == null || argumentsJson.isBlank()
            ? objectMapper.createObjectNode()
            : objectMapper.readTree( argumentsJson );
   }

   /**
    * @throws IllegalArgumentException if the text is not a valid URN
    */
   static AspectModelUrn urn( final String urn ) {
      return AspectModelUrn.from( urn ).toJavaOptional().orElseThrow( () -> new IllegalArgumentException( "Invalid URN: " + urn ) );
   }

   static String required( final JsonNode args, final String field ) {
      final String value = optional( args, field );
      if ( value == null || value.isBlank() ) {
         throw new IllegalArgumentException( "The argument '" + field + "' is required" );
      }
      return value;
   }

   static String text( final JsonNode args, final String field ) {
      final String value = optional( args, field );
      return value == null ? "" : value;
   }

   static int integerArgument( final JsonNode args, final String field, final int fallback ) {
      final JsonNode value = args.get( field );
      if ( value == null || value.isNull() || value.asText().isBlank() ) {
         return fallback;
      }
      if ( value.canConvertToInt() ) {
         return value.asInt();
      }
      try {
         return Integer.parseInt( value.asText().strip() );
      } catch ( final NumberFormatException exception ) {
         throw new IllegalArgumentException( "The argument '" + field + "' must be a number" );
      }
   }

   static @Nullable String optional( final JsonNode args, final String field ) {
      final JsonNode value = args.get( field );
      return value == null || value.isNull() ? null : value.asText();
   }

   static String optional( final JsonNode args, final String field, final String fallback ) {
      final String value = optional( args, field );
      return value == null ? fallback : value;
   }

   static String title( final JsonNode args, final String fallback ) {
      final String title = optional( args, "title" );
      return title == null || title.isBlank() ? fallback : title;
   }

   static String oneOf( final JsonNode node, final String field, final List<String> allowed, final String fallback ) {
      final String value = node.path( field ).asText( "" ).toLowerCase( Locale.ROOT );
      return allowed.contains( value ) ? value : fallback;
   }
}
