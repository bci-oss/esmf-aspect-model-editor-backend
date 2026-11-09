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
package org.eclipse.esmf.ame.services.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.esmf.ame.services.models.workspace.ElementLocation;
import org.eclipse.esmf.aspectmodel.resolver.parser.SmartToken;
import org.eclipse.esmf.aspectmodel.resolver.parser.TokenRegistry;
import org.eclipse.esmf.aspectmodel.resolver.services.TurtleLoader;
import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;

import jakarta.inject.Singleton;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

/**
 * Finds the file and the source position at which a model element is declared.
 */
@Singleton
public class ElementLocationService {
   private final WorkspaceReferenceService workspaceReferenceService;

   public ElementLocationService( final WorkspaceReferenceService workspaceReferenceService ) {
      this.workspaceReferenceService = workspaceReferenceService;
   }

   /**
    * Locates the declaration of a model element in the workspace sources.
    *
    * @param urn the URN of the element
    * @return the location, empty if no file of the element's namespace version declares it
    */
   public Optional<ElementLocation> locate( final AspectModelUrn urn ) {
      return locate( urn, null );
   }

   /**
    * Locates the declaration of a model element in the workspace sources. If several files of the element's namespace
    * version declare it, the preferred file wins.
    *
    * @param urn the URN of the element
    * @param preferredFileName the name of the file to look in first, e.g. the file of the model shown in the editor;
    *       {@code null} for none
    * @return the location, empty if no file of the element's namespace version declares it
    */
   public Optional<ElementLocation> locate( final AspectModelUrn urn, final String preferredFileName ) {
      return workspaceReferenceService.findDefiningFile( urn.getNamespaceMainPart(), urn.getVersion(), urn.toString(),
                  preferredFileName )
            .map( file -> locateIn( file, urn.toString() ) );
   }

   /**
    * Determines the position of the element's declaration in a file. Falls back to the start of the file if the
    * parser provides no position.
    */
   static ElementLocation locateIn( final Path file, final String elementUri ) {
      final String path = file.toAbsolutePath().normalize().toString();
      return declarationToken( file, elementUri )
            .map( token -> new ElementLocation( path, Math.max( 1, token.line() ), Math.max( 1, token.column() ) ) )
            .orElseGet( () -> new ElementLocation( path, 1, 1 ) );
   }

   private static Optional<SmartToken> declarationToken( final Path file, final String elementUri ) {
      final String content;
      try {
         content = Files.readString( file, StandardCharsets.UTF_8 );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not read " + file, e );
      }
      final Model model = TurtleLoader.loadTurtle( content, file.toUri() ).getOrElse( (Model) null );
      if ( model == null ) {
         return Optional.empty();
      }
      // The token registry is keyed by node identity, so the node must be taken from the parsed statements.
      final Resource element = model.createResource( elementUri );
      return Optional.ofNullable( model.getProperty( element, RDF.type ) )
            .flatMap( statement -> TokenRegistry.getToken( statement.getSubject().asNode() ) );
   }
}
