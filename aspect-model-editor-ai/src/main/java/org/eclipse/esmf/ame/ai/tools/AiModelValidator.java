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

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.esmf.ame.ai.model.validation.AiValidationError;
import org.eclipse.esmf.ame.ai.model.validation.AiValidationResult;
import org.eclipse.esmf.ame.constants.ApplicationConstants;
import org.eclipse.esmf.ame.services.AspectModelValidationService;
import org.eclipse.esmf.ame.services.GenerateService;
import org.eclipse.esmf.ame.services.ModelService;
import org.eclipse.esmf.ame.validation.model.ViolationError;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates Turtle produced by the AI (or edited by the user) and generates sample payloads, using the existing
 * services. The Turtle is resolved as if it was the file {@code <namespace>/<version>/<fileName>} of the workspace, so
 * that references to other workspace files can be resolved.
 */
@Singleton
public class AiModelValidator {
   static final String DEFAULT_FILE_NAME = "ai-proposal.ttl";
   private static final Logger LOG = LoggerFactory.getLogger( AiModelValidator.class );
   private static final Pattern SEGMENT = Pattern.compile( "[A-Za-z0-9][A-Za-z0-9._-]*" );
   private static final Pattern EMPTY_PREFIX = Pattern.compile( "@prefix\\s*:\\s*<urn:samm:([^:>]+):([^#>]+)#>" );

   private final AspectModelValidationService validationService;
   private final GenerateService generateService;
   private final ModelService modelService;

   public AiModelValidator( final AspectModelValidationService validationService, final GenerateService generateService,
         final ModelService modelService ) {
      this.validationService = validationService;
      this.generateService = generateService;
      this.modelService = modelService;
   }

   /**
    * Validates the Turtle.
    *
    * @param turtle the complete Turtle of a model file
    * @param namespace the namespace of the file, if known
    * @param version the version of the file, if known
    * @param fileName the name of the file, if known
    * @return the result; never throws for invalid models
    */
   public AiValidationResult validate( final String turtle, final @Nullable String namespace, final @Nullable String version,
         final @Nullable String fileName ) {
      if ( turtle == null || turtle.isBlank() ) {
         return new AiValidationResult( false, List.of( new AiValidationError( "The Turtle is empty.", null ) ) );
      }
      final Location location = locate( turtle, namespace, version, fileName );
      try {
         final List<AiValidationError> errors = validationService
               .validate( location.uri(), TurtleUploads.of( turtle, location.fileName() ) )
               .violationErrors().stream()
               .map( AiModelValidator::toError )
               .toList();
         return new AiValidationResult( errors.isEmpty(), errors );
      } catch ( final RuntimeException e ) {
         LOG.debug( "The AI model could not be loaded", e );
         final String message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
         return new AiValidationResult( false, List.of( new AiValidationError( message, null ) ) );
      }
   }

   /**
    * Generates a sample JSON payload for the Aspect of the Turtle.
    */
   public String samplePayload( final String turtle, final @Nullable String namespace, final @Nullable String version,
         final @Nullable String fileName ) {
      final Location location = locate( turtle, namespace, version, fileName );
      return generateService.sampleJSONPayload( TurtleUploads.of( turtle, location.fileName() ), location.uri() );
   }

   private static AiValidationError toError( final ViolationError error ) {
      final String message = error.getMessage() == null ? "Validation error" : error.getMessage();
      final String focusNode = error.getFocusNode() == null ? null : error.getFocusNode().toString();
      return new AiValidationError( message, focusNode, null, error.getErrorCode() );
   }

   Location locate( final String turtle, final @Nullable String namespace, final @Nullable String version,
         final @Nullable String fileName ) {
      final Optional<String[]> fromPrefix = namespaceOf( turtle );
      final String ns = valid( namespace ) ? namespace
            : fromPrefix.map( value -> value[0] ).filter( AiModelValidator::valid ).orElse( null );
      final String ver = valid( version ) ? version
            : fromPrefix.map( value -> value[1] ).filter( AiModelValidator::valid ).orElse( null );
      final String file = valid( fileName ) ? fileName : DEFAULT_FILE_NAME;
      if ( ns == null || ver == null ) {
         return new Location( blobUri( file ), file );
      }
      final Path root = modelService.getModelPath().toAbsolutePath().normalize();
      final Path path = root.resolve( ns ).resolve( ver ).resolve( file ).normalize();
      return new Location( path.startsWith( root ) ? path.toUri() : blobUri( file ), file );
   }

   private static URI blobUri( final String fileName ) {
      return URI.create( ApplicationConstants.Schemes.BLOB + ":///" + fileName );
   }

   static Optional<String[]> namespaceOf( final String turtle ) {
      final Matcher matcher = EMPTY_PREFIX.matcher( turtle );
      return matcher.find() ? Optional.of( new String[] { matcher.group( 1 ), matcher.group( 2 ) } ) : Optional.empty();
   }

   private static boolean valid( final @Nullable String segment ) {
      return segment != null && SEGMENT.matcher( segment ).matches() && !segment.contains( ".." );
   }

   record Location( URI uri, String fileName ) {
   }
}
