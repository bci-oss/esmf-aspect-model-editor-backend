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

import org.eclipse.esmf.ame.ai.model.validation.AiValidationError;
import org.eclipse.esmf.metamodel.Unit;

import io.micronaut.core.annotation.Nullable;

/**
 * Formats the text returned by the tools to the AI and the summaries shown to the user.
 */
final class ToolOutput {
   static final int MAX_OUTPUT = 60_000;
   private static final int MAX_DESCRIPTION = 120;

   private ToolOutput() {
   }

   /**
    * Formats a count with a noun, e.g. {@code 1 unit} or {@code 2 units}.
    */
   static String plural( final int count, final String noun ) {
      return count + " " + noun + ( count == 1 ? "" : "s" );
   }

   /**
    * Wraps model content in {@code <model>} tags, so that the AI treats it as data. Long content is truncated.
    */
   static String wrap( final String turtle, final @Nullable String fileName ) {
      final String content = turtle.length() > MAX_OUTPUT
            ? turtle.substring( 0, MAX_OUTPUT ) + "\n# ... truncated ..."
            : turtle;
      return "<model" + ( fileName == null ? "" : " file=\"" + fileName.replace( "\"", "" ) + "\"" ) + ">\n" + content + "\n</model>";
   }

   static String formatErrors( final List<AiValidationError> errors ) {
      final StringBuilder output = new StringBuilder();
      errors.forEach( error -> {
         output.append( "- " ).append( error.message() );
         if ( error.focusNode() != null ) {
            output.append( " (element " ).append( error.focusNode() ).append( ')' );
         }
         output.append( '\n' );
      } );
      return output.toString();
   }

   static String format( final TurtleElements.Element element, final String source ) {
      final StringBuilder line = new StringBuilder( "- " ).append( element.urn() );
      if ( element.type() != null ) {
         line.append( " (" ).append( element.type() ).append( ')' );
      }
      if ( element.preferredName() != null ) {
         line.append( " \"" ).append( element.preferredName() ).append( '"' );
      }
      if ( element.description() != null ) {
         final String description = element.description().replaceAll( "\\s+", " " );
         line.append( ": " ).append( description.length() > MAX_DESCRIPTION
               ? description.substring( 0, MAX_DESCRIPTION - 3 ) + "..."
               : description );
      }
      return line.append( " [" ).append( source ).append( ']' ).toString();
   }

   static String format( final Unit unit ) {
      final StringBuilder line = new StringBuilder( "- unit:" ).append( unit.getName() );
      unit.getSymbol().ifPresent( symbol -> line.append( ", symbol " ).append( symbol ) );
      unit.getCode().ifPresent( code -> line.append( ", code " ).append( code ) );
      if ( !unit.getQuantityKinds().isEmpty() ) {
         line.append( ", quantity kinds " ).append( String.join( ", ",
               unit.getQuantityKinds().stream().map( kind -> kind.getName() ).sorted().toList() ) );
      }
      return line.toString();
   }
}
