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

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.micronaut.core.annotation.Nullable;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;

/**
 * Reads elements of Turtle model files, e.g. the definition of one element or the elements matching a search text.
 */
public final class TurtleElements {
   private TurtleElements() {
   }

   /**
    * An element of a model file.
    */
   public record Element( String urn, @Nullable String type, @Nullable String preferredName, @Nullable String description ) {
   }

   /**
    * Parses a Turtle text.
    *
    * @param turtle the Turtle text
    * @return the RDF model
    * @throws org.apache.jena.riot.RiotException if the text is no valid Turtle
    */
   public static Model parse( final String turtle ) {
      final Model model = ModelFactory.createDefaultModel();
      model.read( new StringReader( turtle ), null, "TURTLE" );
      return model;
   }

   /**
    * Extracts the statements describing an element (including its blank nodes) as Turtle.
    *
    * @return the Turtle, empty if the file does not define the element
    */
   public static Optional<String> extract( final String turtle, final String urn ) {
      final Model model = parse( turtle );
      final Resource subject = model.createResource( urn );
      if ( !model.contains( subject, null ) ) {
         return Optional.empty();
      }
      final Model element = ModelFactory.createDefaultModel();
      element.setNsPrefixes( model.getNsPrefixMap() );
      final Deque<Resource> queue = new ArrayDeque<>( List.of( subject ) );
      final Set<Resource> visited = new HashSet<>();
      while ( !queue.isEmpty() ) {
         final Resource current = queue.pop();
         if ( !visited.add( current ) ) {
            continue;
         }
         for ( final Statement statement : model.listStatements( current, null, (RDFNode) null ).toList() ) {
            element.add( statement );
            if ( statement.getObject().isAnon() ) {
               queue.push( statement.getObject().asResource() );
            }
         }
      }
      final StringWriter writer = new StringWriter();
      RDFDataMgr.write( writer, element, RDFFormat.TURTLE_PRETTY );
      return Optional.of( writer.toString() );
   }

   /**
    * Lists the named elements of a model file whose name, preferred name or description contains the text
    * (case-insensitive). A blank text matches all elements.
    */
   public static List<Element> search( final String turtle, final @Nullable String text ) {
      final String query = text == null ? "" : text.trim().toLowerCase( Locale.ROOT );
      final Model model = parse( turtle );
      final List<Element> result = new ArrayList<>();
      for ( final Resource subject : model.listSubjectsWithProperty( RDF.type ).toList() ) {
         if ( !subject.isURIResource() ) {
            continue;
         }
         final String urn = subject.getURI();
         final String type = subject.getPropertyResourceValue( RDF.type ) == null ? null
               : localName( subject.getPropertyResourceValue( RDF.type ).getURI() );
         final String preferredName = literal( subject, "preferredName" );
         final String description = literal( subject, "description" );
         final String haystack = String.join( " ", localName( urn ), Objects.toString( preferredName, "" ),
               Objects.toString( description, "" ) ).toLowerCase( Locale.ROOT );
         if ( query.isEmpty() || haystack.contains( query ) ) {
            result.add( new Element( urn, type, preferredName, description ) );
         }
      }
      result.sort( ( a, b ) -> a.urn().compareTo( b.urn() ) );
      return result;
   }

   private static @Nullable String literal( final Resource subject, final String localName ) {
      final List<String> values = new ArrayList<>();
      subject.listProperties().forEachRemaining( statement -> {
         if ( localName.equals( statement.getPredicate().getLocalName() ) && statement.getObject().isLiteral() ) {
            final String language = statement.getLiteral().getLanguage();
            final String value = statement.getLiteral().getLexicalForm();
            values.add( "en".equals( language ) ? 0 : values.size(), value );
         }
      } );
      return values.isEmpty() ? null : values.get( 0 );
   }

   static String localName( final @Nullable String uri ) {
      if ( uri == null ) {
         return "";
      }
      final int index = Math.max( uri.lastIndexOf( '#' ), uri.lastIndexOf( '/' ) );
      return index < 0 ? uri : uri.substring( index + 1 );
   }
}
