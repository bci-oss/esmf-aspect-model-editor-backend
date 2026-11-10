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

import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.core.annotation.Nullable;

/**
 * Reads the JSON arguments of a tool call. Missing required arguments are reported as
 * {@link IllegalArgumentException}, which is returned to the AI.
 */
final class ToolArguments {
   private ToolArguments() {
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

   static String title( final JsonNode args, final String fallback ) {
      final String title = optional( args, "title" );
      return title == null || title.isBlank() ? fallback : title;
   }

   static String oneOf( final JsonNode node, final String field, final List<String> allowed, final String fallback ) {
      final String value = node.path( field ).asText( "" ).toLowerCase( Locale.ROOT );
      return allowed.contains( value ) ? value : fallback;
   }
}
