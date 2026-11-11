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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ToolArgumentsTest {
   private static final ObjectMapper JSON = new ObjectMapper();

   private static JsonNode args( final String json ) throws IOException {
      return ToolArguments.parse( JSON, json );
   }

   @Test
   void parsesMissingArgumentsAsEmptyObject() throws IOException {
      assertTrue( args( null ).isEmpty() );
      assertTrue( args( "  " ).isEmpty() );
      assertTrue( args( null ).isObject() );
   }

   @Test
   void rejectsInvalidJson() {
      assertThrows( IOException.class, () -> args( "{not json" ) );
   }

   @Test
   void readsRequiredAndOptionalArguments() throws IOException {
      final JsonNode args = args( "{\"name\":\"speed\",\"blank\":\" \",\"none\":null}" );

      assertEquals( "speed", ToolArguments.required( args, "name" ) );
      final IllegalArgumentException missing = assertThrows( IllegalArgumentException.class,
            () -> ToolArguments.required( args, "blank" ) );
      assertEquals( "The argument 'blank' is required", missing.getMessage() );
      assertNull( ToolArguments.optional( args, "none" ) );
      assertEquals( "fallback", ToolArguments.optional( args, "none", "fallback" ) );
      assertEquals( "fallback", ToolArguments.optional( args, "missing", "fallback" ) );
      assertEquals( " ", ToolArguments.optional( args, "blank", "fallback" ) );
      assertEquals( "", ToolArguments.text( args, "missing" ) );
   }

   @Test
   void readsIntegers() throws IOException {
      final JsonNode args = args( "{\"number\":5,\"text\":\" 7 \",\"word\":\"many\"}" );

      assertEquals( 5, ToolArguments.integerArgument( args, "number", 1 ) );
      assertEquals( 7, ToolArguments.integerArgument( args, "text", 1 ) );
      assertEquals( 1, ToolArguments.integerArgument( args, "missing", 1 ) );
      assertThrows( IllegalArgumentException.class, () -> ToolArguments.integerArgument( args, "word", 1 ) );
   }

   @Test
   void readsTitlesAndChoices() throws IOException {
      final JsonNode args = args( "{\"title\":\"  \",\"severity\":\"WARNING\"}" );

      assertEquals( "Default", ToolArguments.title( args, "Default" ) );
      assertEquals( "warning", ToolArguments.oneOf( args, "severity", List.of( "info", "warning" ), "info" ) );
      assertEquals( "info", ToolArguments.oneOf( args, "missing", List.of( "info", "warning" ), "info" ) );
   }

   @Test
   void parsesUrns() {
      assertEquals( "speed", ToolArguments.urn( "urn:samm:org.example:1.0.0#speed" ).getName() );
      final IllegalArgumentException invalid = assertThrows( IllegalArgumentException.class, () -> ToolArguments.urn( "speed" ) );
      assertEquals( "Invalid URN: speed", invalid.getMessage() );
   }
}
