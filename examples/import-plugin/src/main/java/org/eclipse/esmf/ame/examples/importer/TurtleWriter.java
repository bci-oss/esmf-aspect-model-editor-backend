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

import java.util.List;

import org.eclipse.esmf.ame.examples.importer.JsonDescriptionImporter.Property;

/**
 * Writes the RDF/Turtle representation of an Aspect with its Properties.
 */
class TurtleWriter {
   private static final String SAMM_VERSION = "2.2.0";

   private final String namespace;
   private final String version;

   TurtleWriter( final String namespace, final String version ) {
      this.namespace = namespace;
      this.version = version;
   }

   String aspect( final String name, final String description, final List<Property> properties ) {
      final StringBuilder turtle = new StringBuilder()
            .append( "@prefix samm: <urn:samm:org.eclipse.esmf.samm:meta-model:" ).append( SAMM_VERSION ).append( "#> .\n" )
            .append( "@prefix samm-c: <urn:samm:org.eclipse.esmf.samm:characteristic:" ).append( SAMM_VERSION ).append( "#> .\n" )
            .append( "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" )
            .append( "@prefix : <urn:samm:" ).append( namespace ).append( ':' ).append( version ).append( "#> .\n\n" )
            .append( ':' ).append( name ).append( " a samm:Aspect ;\n" );
      description( turtle, description );
      turtle.append( "   samm:properties ( " )
            .append( String.join( " ", properties.stream().map( property -> ":" + property.name() ).toList() ) )
            .append( " ) ;\n   samm:operations ( ) .\n" );

      for ( final Property property : properties ) {
         turtle.append( "\n:" ).append( property.name() ).append( " a samm:Property ;\n" );
         description( turtle, property.description() );
         turtle.append( "   samm:characteristic " ).append( characteristic( property ) ).append( " .\n" );
      }
      for ( final Property property : properties ) {
         if ( predefinedCharacteristic( property.type() ) == null ) {
            turtle.append( "\n:" ).append( characteristicName( property ) ).append( " a samm:Characteristic ;\n" )
                  .append( "   samm:dataType xsd:" ).append( property.type() ).append( " .\n" );
         }
      }
      return turtle.toString();
   }

   private static void description( final StringBuilder turtle, final String description ) {
      if ( !description.isBlank() ) {
         turtle.append( "   samm:description \"" ).append( escape( description ) ).append( "\"@en ;\n" );
      }
   }

   private static String characteristic( final Property property ) {
      final String predefined = predefinedCharacteristic( property.type() );
      return predefined != null ? predefined : ":" + characteristicName( property );
   }

   private static String predefinedCharacteristic( final String type ) {
      return switch ( type ) {
         case "string" -> "samm-c:Text";
         case "boolean" -> "samm-c:Boolean";
         case "dateTime" -> "samm-c:Timestamp";
         default -> null;
      };
   }

   private static String characteristicName( final Property property ) {
      return Character.toUpperCase( property.name().charAt( 0 ) ) + property.name().substring( 1 ) + "Characteristic";
   }

   private static String escape( final String value ) {
      return value.replace( "\\", "\\\\" ).replace( "\"", "\\\"" ).replace( "\n", "\\n" ).replace( "\r", "\\r" );
   }
}
