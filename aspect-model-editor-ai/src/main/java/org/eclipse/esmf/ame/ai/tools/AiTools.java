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

import static org.eclipse.esmf.ame.ai.tools.ToolArguments.integerArgument;
import static org.eclipse.esmf.ame.ai.tools.ToolArguments.required;
import static org.eclipse.esmf.ame.ai.tools.ToolArguments.text;
import static org.eclipse.esmf.ame.ai.tools.ToolSchemas.array;
import static org.eclipse.esmf.ame.ai.tools.ToolSchemas.bool;
import static org.eclipse.esmf.ame.ai.tools.ToolSchemas.enumeration;
import static org.eclipse.esmf.ame.ai.tools.ToolSchemas.integer;
import static org.eclipse.esmf.ame.ai.tools.ToolSchemas.object;
import static org.eclipse.esmf.ame.ai.tools.ToolSchemas.string;
import static org.eclipse.esmf.ame.ai.tools.ToolSchemas.tool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.esmf.ame.ai.attachment.AiAttachmentStore;
import org.eclipse.esmf.ame.ai.chat.AiPrompts;
import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;
import org.eclipse.esmf.ame.ai.model.proposal.AiReviewFinding;
import org.eclipse.esmf.ame.ai.provider.AiToolSpec;
import org.eclipse.esmf.ame.services.ModelService;
import org.eclipse.esmf.ame.services.workspace.ElementUsageService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The tools the AI may call. Read-only tools reuse the existing services; proposal tools only emit proposals (they never
 * write files). Model content is always returned wrapped in {@code <model>} tags, so that it is treated as data.
 */
@Singleton
public class AiTools {
   public static final String GET_OPEN_MODEL = "getOpenModel";
   public static final String GET_ELEMENT = "getElement";
   public static final String SEARCH_ELEMENTS = "searchElements";
   public static final String GET_VALIDATION_ERRORS = "getValidationErrors";
   public static final String FIND_USAGES = "findUsages";
   public static final String LIST_UNITS = "listUnits";
   public static final String LIST_CHARACTERISTICS = "listCharacteristics";
   public static final String GENERATE_SAMPLE_PAYLOAD = "generateSamplePayload";
   public static final String LIST_WORKSPACE_MODELS = "listWorkspaceModels";
   public static final String READ_MODEL_FILE = "readModelFile";
   public static final String READ_ATTACHMENT = "readAttachment";
   public static final String PROPOSE_MODEL_CHANGE = "proposeModelChange";
   public static final String PROPOSE_NEW_MODEL = "proposeNewModel";
   public static final String PROPOSE_RENAME = "proposeRename";
   public static final String PROPOSE_PAYLOAD = "proposePayload";
   public static final String REPORT_REVIEW = "reportReview";

   /** How often an invalid model may be returned to the AI for correction before it is proposed as invalid. */
   public static final int MAX_SELF_CORRECTIONS = 2;

   private static final Logger LOG = LoggerFactory.getLogger( AiTools.class );

   private final ObjectMapper objectMapper = new ObjectMapper();
   private final ModelReadTools readTools;
   private final ModelProposalTools proposalTools;
   private final ContextReadTools contextTools;

   /**
    * Creates the tools.
    *
    * @param validator validates proposed models and generates sample payloads
    * @param modelService reads workspace models
    * @param elementUsageService finds usages of elements
    * @param workspaceReferenceService lists the workspace files
    * @param attachmentStore keeps the attached files
    */
   public AiTools( final AiModelValidator validator, final ModelService modelService, final ElementUsageService elementUsageService,
         final WorkspaceReferenceService workspaceReferenceService, final AiAttachmentStore attachmentStore ) {
      readTools = new ModelReadTools( validator, modelService, elementUsageService, workspaceReferenceService );
      contextTools = new ContextReadTools( attachmentStore, workspaceReferenceService );
      proposalTools = new ModelProposalTools( objectMapper, validator );
   }

   /**
    * The tools offered to the AI for the given context. Tools changing the open model are not offered for read-only
    * models.
    */
   public List<AiToolSpec> specs( final AiChatContext context ) {
      final List<AiToolSpec> specs = new ArrayList<>();
      if ( context.hasModel() ) {
         specs.add( tool( GET_OPEN_MODEL, "Returns the complete Turtle of the model currently opened in the editor.", Map.of(),
               List.of() ) );
      }
      specs.add( tool( GET_ELEMENT, "Returns the Turtle definition of a model element (open model or workspace) by its URN.",
            Map.of( "urn", string( "The element URN, e.g. urn:samm:org.example:1.0.0#Movement" ) ), List.of( "urn" ) ) );
      specs.add( tool( SEARCH_ELEMENTS,
            "Searches elements of the open model and of all workspace models (including GitHub sources) by name, preferred name or "
                  + "description.", Map.of( "text", string( "The search text; empty lists all elements" ) ), List.of( "text" ) ) );
      if ( context.hasModel() ) {
         specs.add( tool( GET_VALIDATION_ERRORS, "Returns the validation errors of the open model.", Map.of(), List.of() ) );
         specs.add( tool( GENERATE_SAMPLE_PAYLOAD, "Generates a sample JSON payload for the Aspect of the open model.", Map.of(),
               List.of() ) );
      }
      specs.add( tool( FIND_USAGES, "Lists the workspace elements that use the element with the given URN.",
            Map.of( "urn", string( "The element URN" ) ), List.of( "urn" ) ) );
      specs.add( tool( LIST_UNITS, "Searches the SAMM unit catalog by name, symbol, code or quantity kind.",
            Map.of( "query", string( "e.g. 'metre', 'km/h', 'temperature'" ) ), List.of( "query" ) ) );
      specs.add( tool( LIST_CHARACTERISTICS, "Lists the SAMM characteristics, characteristic classes and constraints.",
            Map.of( "query", string( "Optional filter text" ) ), List.of() ) );
      specs.add( tool( LIST_WORKSPACE_MODELS, "Lists the model files of the workspace (namespace, version, file name, read-only).",
            Map.of(), List.of() ) );
      specs.add( tool( READ_MODEL_FILE, "Returns the complete Turtle of a model file of the workspace (including GitHub sources).",
            Map.of( "namespace", string( "The namespace, e.g. org.example" ), "version", string( "The version, e.g. 1.0.0" ),
                  "fileName", string( "The file name, e.g. Movement.ttl" ) ), List.of( "namespace", "version", "fileName" ) ) );
      if ( !context.attachments().isEmpty() ) {
         specs.add( tool( READ_ATTACHMENT, "Returns a part of the text of a file attached by the user ("
                     + AiPrompts.ATTACHMENT_PART_LENGTH + " characters per part).",
               Map.of( "id", string( "The attachment id" ), "part", integer( "The part to read, starting at 1" ) ), List.of( "id" ) ) );
      }
      if ( context.hasModel() && !context.isReadOnly() ) {
         specs.add( tool( PROPOSE_MODEL_CHANGE,
               "Proposes a change of the open model to the user. 'turtle' must be the COMPLETE new Turtle of the model file "
                     + "(all prefixes and elements). The Turtle is validated; validation errors are returned for correction.",
               Map.of( "turtle", string( "The complete new Turtle of the model file" ), "title", string( "Short title of the change" ),
                     "summary", string( "Markdown summary of the change" ) ), List.of( "turtle", "title" ) ) );
      }
      specs.add( tool( PROPOSE_NEW_MODEL,
            "Proposes a new model file to the user. 'turtle' must be the COMPLETE Turtle of the file. The Turtle is validated; "
                  + "validation errors are returned for correction.",
            Map.of( "turtle", string( "The complete Turtle of the new model file" ),
                  "namespace", string( "The namespace, e.g. org.example" ), "version", string( "The version, e.g. 1.0.0" ),
                  "fileName", string( "The file name, e.g. Movement.ttl" ), "title", string( "Short title" ),
                  "summary", string( "Markdown summary" ) ), List.of( "turtle", "namespace", "version", "fileName", "title" ) ) );
      if ( context.hasModel() && !context.isReadOnly() ) {
         specs.add( tool( PROPOSE_RENAME, "Proposes to rename an element of the open model (also updates all references).",
               Map.of( "urn", string( "The URN of the element to rename" ), "newName", string( "The new local name" ),
                     "title", string( "Short title" ) ), List.of( "urn", "newName" ) ) );
      }
      specs.add( tool( PROPOSE_PAYLOAD, "Proposes one or more example JSON payloads (e.g. valid, boundary values) to the user.",
            Map.of( "variants", array( object( Map.of( "name", string( "Name of the variant, e.g. 'valid'" ),
                        "json", string( "The JSON payload as text" ) ), List.of( "name", "json" ) ), "The payload variants" ),
                  "title", string( "Short title" ) ), List.of( "variants" ) ) );
      specs.add( tool( REPORT_REVIEW, "Reports the findings of a model review to the user.",
            Map.of( "findings", array( object( reviewFindingProperties(),
                        List.of( "severity", "category", "message", "fixable" ) ), "The findings" ),
                  "title", string( "Short title" ), "summary", string( "Markdown summary of the review" ) ), List.of( "findings" ) ) );
      return specs;
   }

   private static Map<String, Object> reviewFindingProperties() {
      final Map<String, Object> properties = new LinkedHashMap<>();
      properties.put( "severity", enumeration( AiReviewFinding.SEVERITIES ) );
      properties.put( "category", enumeration( AiReviewFinding.CATEGORIES ) );
      properties.put( "elementUrn", string( "The URN of the affected element" ) );
      properties.put( "message", string( "The finding" ) );
      properties.put( "suggestion", string( "How to fix it" ) );
      properties.put( "reuseUrn", string( "URN of an existing element that could be reused instead" ) );
      properties.put( "fixable", bool( "Whether the AI can fix it with a model change" ) );
      return properties;
   }

   /**
    * Executes a tool call. Never throws for invalid arguments or failing services; the failure is returned to the AI.
    */
   public AiToolResult execute( final AiToolSession session, final String name, final @Nullable String argumentsJson ) {
      final JsonNode args;
      try {
         args = ToolArguments.parse( objectMapper, argumentsJson );
      } catch ( final IOException e ) {
         return ToolFailures.invalidJson( e );
      }
      try {
         return switch ( name ) {
            case GET_OPEN_MODEL -> readTools.getOpenModel( session.context() );
            case GET_ELEMENT -> readTools.getElement( session.context(), required( args, "urn" ) );
            case SEARCH_ELEMENTS -> readTools.searchElements( session.context(), text( args, "text" ) );
            case GET_VALIDATION_ERRORS -> readTools.getValidationErrors( session.context() );
            case FIND_USAGES -> readTools.findUsages( required( args, "urn" ) );
            case LIST_UNITS -> readTools.listUnits( text( args, "query" ) );
            case LIST_CHARACTERISTICS -> readTools.listCharacteristics( text( args, "query" ) );
            case GENERATE_SAMPLE_PAYLOAD -> readTools.generateSamplePayload( session.context() );
            case LIST_WORKSPACE_MODELS -> readTools.listWorkspaceModels();
            case READ_MODEL_FILE -> contextTools.readModelFile( required( args, "namespace" ), required( args, "version" ),
                  required( args, "fileName" ) );
            case READ_ATTACHMENT -> contextTools.readAttachment( required( args, "id" ), integerArgument( args, "part", 1 ) );
            case PROPOSE_MODEL_CHANGE -> proposalTools.proposeModelChange( session, args );
            case PROPOSE_NEW_MODEL -> proposalTools.proposeNewModel( session, args );
            case PROPOSE_RENAME -> proposalTools.proposeRename( session, args );
            case PROPOSE_PAYLOAD -> proposalTools.proposePayload( session, args );
            case REPORT_REVIEW -> proposalTools.reportReview( session, args );
            default -> ToolFailures.unknownTool( name );
         };
      } catch ( final IllegalArgumentException e ) {
         return ToolFailures.invalidArgument( e );
      } catch ( final RuntimeException e ) {
         LOG.debug( "AI tool {} failed", name, e );
         return ToolFailures.failed( e );
      }
   }
}
