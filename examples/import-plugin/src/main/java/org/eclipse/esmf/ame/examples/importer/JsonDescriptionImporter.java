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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.esmf.ame.plugin.api.ImportResult;
import org.eclipse.esmf.ame.plugin.api.ImportSource;
import org.eclipse.esmf.ame.plugin.api.ImportedModel;
import org.eclipse.esmf.ame.plugin.api.ModelImporter;
import org.eclipse.esmf.ame.plugin.api.PluginException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Converts a JSON description of an Aspect into an Aspect Model:
 * <pre>
 * {
 *   "namespace": "org.example", "version": "1.0.0",
 *   "aspect": { "name": "Movement", "description": "...",
 *     "properties": [ { "name": "speed", "type": "float", "description": "..." } ] }
 * }
 * </pre>
 * The option {@code namespace} overrides the namespace of the file.
 */
class JsonDescriptionImporter implements ModelImporter {
   static final String NAMESPACE_OPTION = "namespace";

   private static final ObjectMapper MAPPER = new ObjectMapper();
   private static final Pattern NAMESPACE = Pattern.compile( "[a-zA-Z][a-zA-Z0-9_-]*(\\.[a-zA-Z0-9_-]+)*" );
   private static final Pattern VERSION = Pattern.compile( "\\d+\\.\\d+\\.\\d+" );
   private static final Pattern ASPECT_NAME = Pattern.compile( "[A-Z][a-zA-Z0-9]*" );
   private static final Pattern PROPERTY_NAME = Pattern.compile( "[a-z][a-zA-Z0-9]*" );
   private static final List<String> TYPES = List.of( "string", "boolean", "int", "long", "float", "double", "dateTime" );

   @Override
   public String id() {
      return "json";
   }

   @Override
   public ImportResult importModel( final ImportSource source ) throws PluginException {
      final JsonNode root = parse( source.content() );
      final String namespace = source.options().getOrDefault( NAMESPACE_OPTION, root.path( "namespace" ).asText( "" ) ).trim();
      final String version = root.path( "version" ).asText( "1.0.0" );
      final JsonNode aspect = root.path( "aspect" );
      final String aspectName = aspect.path( "name" ).asText( "" );

      require( NAMESPACE.matcher( namespace ).matches(), "The namespace '" + namespace + "' is invalid, e.g. 'org.example'." );
      require( VERSION.matcher( version ).matches(), "The version '" + version + "' is invalid, e.g. '1.0.0'." );
      require( ASPECT_NAME.matcher( aspectName ).matches(), "The aspect needs a name starting with an uppercase letter, e.g. 'Movement'." );

      final List<String> warnings = new ArrayList<>();
      final List<Property> properties = new ArrayList<>();
      for ( final JsonNode property : aspect.path( "properties" ) ) {
         final String name = property.path( "name" ).asText( "" );
         final String type = property.path( "type" ).asText( "string" );
         if ( !PROPERTY_NAME.matcher( name ).matches() ) {
            warnings.add( "Skipped the property '" + name + "': names start with a lowercase letter." );
         } else if ( !TYPES.contains( type ) ) {
            warnings.add( "Skipped the property '" + name + "': the type '" + type + "' is not supported." );
         } else {
            properties.add( new Property( name, type, property.path( "description" ).asText( "" ) ) );
         }
      }

      final String content = new TurtleWriter( namespace, version ).aspect( aspectName, aspect.path( "description" ).asText( "" ),
            properties );
      return new ImportResult( List.of( new ImportedModel( namespace, version, aspectName + ".ttl", content ) ), warnings );
   }

   private static JsonNode parse( final byte[] content ) throws PluginException {
      try {
         final JsonNode root = MAPPER.readTree( content );
         if ( root == null || !root.isObject() ) {
            throw new PluginException( "The file is not a JSON description: a JSON object is expected." );
         }
         return root;
      } catch ( final JsonProcessingException e ) {
         throw new PluginException( "The file is not valid JSON: " + e.getOriginalMessage(), e );
      } catch ( final IOException e ) {
         throw new PluginException( "The file could not be read.", e );
      }
   }

   private static void require( final boolean condition, final String message ) throws PluginException {
      if ( !condition ) {
         throw new PluginException( message );
      }
   }

   record Property( String name, String type, String description ) {}
}
