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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.esmf.ame.ai.provider.AiToolSpec;

/**
 * Builds the JSON schemas of the tool parameters.
 */
final class ToolSchemas {
   private ToolSchemas() {
   }

   static AiToolSpec tool( final String name, final String description, final Map<String, Object> properties,
         final List<String> required ) {
      return new AiToolSpec( name, description, object( properties, required ) );
   }

   static Map<String, Object> object( final Map<String, Object> properties, final List<String> required ) {
      final Map<String, Object> schema = new LinkedHashMap<>();
      schema.put( "type", "object" );
      schema.put( "properties", new LinkedHashMap<>( properties ) );
      schema.put( "required", required );
      return schema;
   }

   static Map<String, Object> string( final String description ) {
      return Map.of( "type", "string", "description", description );
   }

   static Map<String, Object> integer( final String description ) {
      return Map.of( "type", "integer", "description", description );
   }

   static Map<String, Object> bool( final String description ) {
      return Map.of( "type", "boolean", "description", description );
   }

   static Map<String, Object> enumeration( final List<String> values ) {
      return Map.of( "type", "string", "enum", values );
   }

   static Map<String, Object> array( final Map<String, Object> items, final String description ) {
      return Map.of( "type", "array", "items", items, "description", description );
   }
}
