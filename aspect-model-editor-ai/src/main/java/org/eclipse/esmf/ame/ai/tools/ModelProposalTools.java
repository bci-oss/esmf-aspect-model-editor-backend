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

import static org.eclipse.esmf.ame.ai.tools.ToolArguments.oneOf;
import static org.eclipse.esmf.ame.ai.tools.ToolArguments.optional;
import static org.eclipse.esmf.ame.ai.tools.ToolArguments.required;
import static org.eclipse.esmf.ame.ai.tools.ToolArguments.text;
import static org.eclipse.esmf.ame.ai.tools.ToolArguments.title;
import static org.eclipse.esmf.ame.ai.tools.ToolOutput.formatErrors;
import static org.eclipse.esmf.ame.ai.tools.ToolOutput.plural;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;
import org.eclipse.esmf.ame.ai.model.proposal.AiPayloadVariant;
import org.eclipse.esmf.ame.ai.model.proposal.AiProposal;
import org.eclipse.esmf.ame.ai.model.proposal.AiRefactoringProposal;
import org.eclipse.esmf.ame.ai.model.proposal.AiReviewFinding;
import org.eclipse.esmf.ame.ai.model.validation.AiValidationResult;
import org.eclipse.esmf.ame.constants.ApplicationConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.Nullable;

/**
 * The proposal tools: they validate what the AI proposes and emit it as {@link AiProposal}. They never write files.
 */
final class ModelProposalTools {
   private static final Pattern ELEMENT_NAME = Pattern.compile( "[A-Za-z][A-Za-z0-9_-]*" );

   private final ObjectMapper objectMapper;
   private final AiModelValidator validator;

   ModelProposalTools( final ObjectMapper objectMapper, final AiModelValidator validator ) {
      this.objectMapper = objectMapper;
      this.validator = validator;
   }

   AiToolResult proposeModelChange( final AiToolSession session, final JsonNode args ) {
      final AiChatContext context = session.context();
      if ( context.isReadOnly() ) {
         return ToolFailures.readOnly();
      }
      if ( !context.hasModel() ) {
         return AiToolResult.failure( "No model is open in the editor; use proposeNewModel instead.", "No model open" );
      }
      final String turtle = required( args, "turtle" );
      final String title = title( args, "Model change" );
      final AiValidationResult validation = validator.validate( turtle, context.namespace(), context.version(), context.fileName() );
      final Optional<AiToolResult> retry = retryIfInvalid( session, validation );
      if ( retry.isPresent() ) {
         return retry.get();
      }
      session.propose( new AiProposal( newId(), AiProposal.MODEL_CHANGE, title, optional( args, "summary" ), turtle,
            context.modelTurtle(), context.namespace(), context.version(), context.fileName(), validation.valid(),
            validation.validationErrors(), null, null, null ) );
      return proposed( validation );
   }

   AiToolResult proposeNewModel( final AiToolSession session, final JsonNode args ) {
      final String turtle = required( args, "turtle" );
      final String namespace = optional( args, "namespace" );
      final String version = optional( args, "version" );
      final String fileName = withTurtleExtension( optional( args, "fileName" ) );
      final AiValidationResult validation = validator.validate( turtle, namespace, version, fileName );
      final Optional<AiToolResult> retry = retryIfInvalid( session, validation );
      if ( retry.isPresent() ) {
         return retry.get();
      }
      session.propose( new AiProposal( newId(), AiProposal.NEW_MODEL, title( args, "New model" ), optional( args, "summary" ), turtle,
            null, namespace, version, fileName, validation.valid(), validation.validationErrors(), null, null, null ) );
      return proposed( validation );
   }

   AiToolResult proposeRename( final AiToolSession session, final JsonNode args ) {
      if ( session.context().isReadOnly() ) {
         return ToolFailures.readOnly();
      }
      final String urn = required( args, "urn" );
      final String newName = required( args, "newName" );
      ToolArguments.urn( urn );
      if ( !ELEMENT_NAME.matcher( newName ).matches() ) {
         throw new IllegalArgumentException( "Invalid element name: " + newName );
      }
      session.propose( new AiProposal( newId(), AiProposal.REFACTORING,
            title( args, "Rename " + TurtleElements.localName( urn ) + " to " + newName ), null, null, null, null, null, null, null, null,
            new AiRefactoringProposal( AiRefactoringProposal.RENAME_ELEMENT, urn, newName ), null, null ) );
      return AiToolResult.ok( "The rename was proposed to the user.", "Proposed a rename" );
   }

   AiToolResult proposePayload( final AiToolSession session, final JsonNode args ) {
      final JsonNode variants = args.get( "variants" );
      if ( variants == null || !variants.isArray() || variants.isEmpty() ) {
         throw new IllegalArgumentException( "'variants' must be a non-empty array" );
      }
      final List<AiPayloadVariant> payload = new ArrayList<>();
      for ( final JsonNode variant : variants ) {
         final String name = optional( variant, "name", "Variant " + ( payload.size() + 1 ) );
         payload.add( new AiPayloadVariant( name, prettyJson( name, variant.get( "json" ) ) ) );
      }
      session.propose( new AiProposal( newId(), AiProposal.PAYLOAD, title( args, "Example payloads" ), null, null, null, null, null,
            null, null, null, null, payload, null ) );
      return AiToolResult.ok( "The payloads were shown to the user. Do not repeat them in your answer.",
            "Proposed " + plural( payload.size(), "payload" ) );
   }

   AiToolResult reportReview( final AiToolSession session, final JsonNode args ) {
      final JsonNode items = args.get( "findings" );
      if ( items == null || !items.isArray() ) {
         throw new IllegalArgumentException( "'findings' must be an array" );
      }
      final List<AiReviewFinding> findings = new ArrayList<>();
      for ( final JsonNode item : items ) {
         final String message = text( item, "message" );
         if ( message.isBlank() ) {
            continue;
         }
         final String severity = oneOf( item, "severity", AiReviewFinding.SEVERITIES, "info" );
         final String category = oneOf( item, "category", AiReviewFinding.CATEGORIES, "other" );
         findings.add( new AiReviewFinding( "f" + ( findings.size() + 1 ), severity, category, optional( item, "elementUrn" ), message,
               optional( item, "suggestion" ), optional( item, "reuseUrn" ), item.path( "fixable" ).asBoolean( false ) ) );
      }
      session.propose( new AiProposal( newId(), AiProposal.REVIEW, title( args, "Model review" ), optional( args, "summary" ), null,
            null, null, null, null, null, null, null, null, findings ) );
      return AiToolResult.ok( "The " + findings.size() + " findings were shown to the user. Summarize them briefly; do not repeat them.",
            plural( findings.size(), "finding" ) );
   }

   private Optional<AiToolResult> retryIfInvalid( final AiToolSession session, final AiValidationResult validation ) {
      if ( validation.valid() ) {
         session.resetInvalidModels();
         return Optional.empty();
      }
      final int attempt = session.countInvalidModel();
      if ( attempt > AiTools.MAX_SELF_CORRECTIONS ) {
         return Optional.empty();
      }
      return Optional.of( AiToolResult.failure( "The Turtle is invalid and was NOT proposed. Fix the following errors and call the tool "
                  + "again with the complete corrected Turtle (attempt " + attempt + " of " + AiTools.MAX_SELF_CORRECTIONS + "):\n"
                  + formatErrors( validation.validationErrors() ),
            plural( validation.validationErrors().size(), "validation error" ) ) );
   }

   private static AiToolResult proposed( final AiValidationResult validation ) {
      if ( validation.valid() ) {
         return AiToolResult.ok( "The proposal is valid and was shown to the user. Do not repeat the Turtle in your answer.",
               "Proposed a valid model" );
      }
      return AiToolResult.ok( "The proposal is still invalid; it was shown to the user together with the errors. Do not try again.",
            "Proposed a model with validation errors" );
   }

   private String prettyJson( final String name, final @Nullable JsonNode json ) {
      if ( json == null || json.isNull() ) {
         throw new IllegalArgumentException( "The variant '" + name + "' has no 'json'" );
      }
      try {
         return objectMapper.writerWithDefaultPrettyPrinter()
               .writeValueAsString( json.isTextual() ? objectMapper.readTree( json.asText() ) : json );
      } catch ( final IOException e ) {
         throw new IllegalArgumentException( "The variant '" + name + "' is not valid JSON: " + e.getMessage() );
      }
   }

   private static @Nullable String withTurtleExtension( final @Nullable String fileName ) {
      if ( fileName == null ) {
         return null;
      }
      final String extension = ApplicationConstants.FileExtensions.TTL;
      return fileName.toLowerCase( Locale.ROOT ).endsWith( extension ) ? fileName : fileName + extension;
   }

   private static String newId() {
      return UUID.randomUUID().toString();
   }
}
