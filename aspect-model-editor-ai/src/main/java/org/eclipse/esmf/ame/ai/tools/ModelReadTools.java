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

import static org.eclipse.esmf.ame.ai.tools.ToolOutput.format;
import static org.eclipse.esmf.ame.ai.tools.ToolOutput.formatErrors;
import static org.eclipse.esmf.ame.ai.tools.ToolOutput.plural;
import static org.eclipse.esmf.ame.ai.tools.ToolOutput.wrap;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;
import org.eclipse.esmf.ame.ai.model.validation.AiValidationError;
import org.eclipse.esmf.ame.services.ModelService;
import org.eclipse.esmf.ame.services.models.AspectModelResult;
import org.eclipse.esmf.ame.services.models.workspace.ElementUsage;
import org.eclipse.esmf.ame.services.models.workspace.ElementUsageReport;
import org.eclipse.esmf.ame.services.workspace.ElementUsageService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService.WorkspaceFile;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;
import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;
import org.eclipse.esmf.metamodel.Unit;
import org.eclipse.esmf.metamodel.Units;

/**
 * The read-only tools: they read the open model, the workspace and the SAMM catalogs using the existing services.
 */
final class ModelReadTools {
   private static final int MAX_RESULTS = 50;
   private static final int MAX_FILES = 500;
   private static final String NO_MODEL_OPEN = "No model is open in the editor.";

   private final AiModelValidator validator;
   private final ModelService modelService;
   private final ElementUsageService elementUsageService;
   private final WorkspaceReferenceService workspaceReferenceService;

   ModelReadTools( final AiModelValidator validator, final ModelService modelService, final ElementUsageService elementUsageService,
         final WorkspaceReferenceService workspaceReferenceService ) {
      this.validator = validator;
      this.modelService = modelService;
      this.elementUsageService = elementUsageService;
      this.workspaceReferenceService = workspaceReferenceService;
   }

   AiToolResult getOpenModel( final AiChatContext context ) {
      if ( !context.hasModel() ) {
         return AiToolResult.failure( NO_MODEL_OPEN, "No model open" );
      }
      return AiToolResult.ok( wrap( context.modelTurtle(), context.fileName() ), "Read the open model" );
   }

   AiToolResult getElement( final AiChatContext context, final String urn ) {
      if ( context.hasModel() ) {
         final Optional<String> element = extractQuietly( context.modelTurtle(), urn );
         if ( element.isPresent() ) {
            return AiToolResult.ok( wrap( element.get(), context.fileName() ), "Read " + TurtleElements.localName( urn ) );
         }
      }
      final Optional<AspectModelResult> file = modelService.getUnresolvedModel( parseUrn( urn ) );
      final Optional<String> element = file.flatMap( result -> extractQuietly( result.content(), urn ) );
      if ( element.isEmpty() ) {
         return AiToolResult.failure( "The element " + urn + " was not found in the open model or the workspace.", "Not found" );
      }
      return AiToolResult.ok( wrap( element.get(), file.flatMap( AspectModelResult::filename ).orElse( null ) ),
            "Read " + TurtleElements.localName( urn ) );
   }

   AiToolResult searchElements( final AiChatContext context, final String text ) {
      final Map<String, String> found = new LinkedHashMap<>();
      if ( context.hasModel() ) {
         searchQuietly( context.modelTurtle(), text ).forEach( element -> found.putIfAbsent( element.urn(), format( element, "open model" ) ) );
      }
      final List<WorkspaceFile> files = workspaceReferenceService.listWorkspaceFiles();
      for ( final WorkspaceFile file : files.subList( 0, Math.min( files.size(), MAX_FILES ) ) ) {
         if ( found.size() >= MAX_RESULTS ) {
            break;
         }
         final String content;
         try {
            content = Files.readString( file.path() );
         } catch ( final IOException | RuntimeException e ) {
            continue;
         }
         final String source = describe( file );
         searchQuietly( content, text ).forEach( element -> found.putIfAbsent( element.urn(), format( element, source ) ) );
      }
      if ( found.isEmpty() ) {
         return AiToolResult.ok( "No elements found for '" + text + "'.", "No elements found" );
      }
      final List<String> lines = found.values().stream().limit( MAX_RESULTS ).toList();
      return AiToolResult.ok( "<model>\n" + String.join( "\n", lines ) + "\n</model>", "Found " + plural( lines.size(), "element" ) );
   }

   AiToolResult getValidationErrors( final AiChatContext context ) {
      if ( !context.hasModel() ) {
         return AiToolResult.failure( NO_MODEL_OPEN, "No model open" );
      }
      final List<AiValidationError> errors = context.validationErrors() != null
            ? context.validationErrors()
            : validator.validate( context.modelTurtle(), context.namespace(), context.version(), context.fileName() ).validationErrors();
      if ( errors.isEmpty() ) {
         return AiToolResult.ok( "The open model is valid.", "No validation errors" );
      }
      return AiToolResult.ok( "<model>\n" + formatErrors( errors ) + "</model>", plural( errors.size(), "validation error" ) );
   }

   AiToolResult findUsages( final String urn ) {
      final AspectModelUrn aspectModelUrn = parseUrn( urn );
      final ElementUsageReport report;
      try {
         report = elementUsageService.findUsages( aspectModelUrn );
      } catch ( final NoSuchElementException e ) {
         return AiToolResult.failure( "No workspace file defines " + urn + ".", "Not found" );
      }
      final StringBuilder output = new StringBuilder();
      if ( report.usages().isEmpty() ) {
         output.append( "The element " ).append( urn ).append( " is not used by other elements.\n" );
      }
      for ( final ElementUsage usage : report.usages() ) {
         output.append( "- " ).append( usage.urn() ).append( " in " ).append( usage.namespace() ).append( ':' ).append( usage.version() )
               .append( '/' ).append( usage.fileName() ).append( usage.external() ? " (other file)" : "" )
               .append( usage.readOnly() ? " (read-only)" : "" ).append( '\n' );
      }
      report.unreadable().forEach( file -> output.append( "- could not check " ).append( file.fileName() ).append( '\n' ) );
      return AiToolResult.ok( output.toString(), plural( report.usages().size(), "usage" ) );
   }

   AiToolResult listUnits( final String query ) {
      final String text = query.trim().toLowerCase( Locale.ROOT );
      final List<String> lines = Units.getUnits().stream()
            .filter( unit -> text.isEmpty() || unitText( unit ).contains( text ) )
            .sorted( ( a, b ) -> a.getName().compareTo( b.getName() ) )
            .limit( MAX_RESULTS )
            .map( ToolOutput::format )
            .toList();
      if ( lines.isEmpty() ) {
         return AiToolResult.ok( "No units found for '" + query + "'.", "No units found" );
      }
      return AiToolResult.ok( "Units (use them as unit:<name>):\n" + String.join( "\n", lines ), "Found " + plural( lines.size(), "unit" ) );
   }

   AiToolResult listCharacteristics( final String query ) {
      final List<SammCharacteristics.Entry> entries = SammCharacteristics.search( query );
      final StringBuilder output = new StringBuilder();
      entries.forEach( entry -> output.append( "- " ).append( entry.name() ).append( " (" ).append( entry.kind() ).append( "): " )
            .append( entry.description() ).append( '\n' ) );
      return AiToolResult.ok( output.isEmpty() ? "No characteristics found." : output.toString(),
            "Found " + plural( entries.size(), "characteristic" ) );
   }

   AiToolResult generateSamplePayload( final AiChatContext context ) {
      if ( !context.hasModel() ) {
         return AiToolResult.failure( NO_MODEL_OPEN, "No model open" );
      }
      final String json = validator.samplePayload( context.modelTurtle(), context.namespace(), context.version(), context.fileName() );
      return AiToolResult.ok( json, "Generated a sample payload" );
   }

   AiToolResult listWorkspaceModels() {
      final List<WorkspaceFile> files = workspaceReferenceService.listWorkspaceFiles();
      if ( files.isEmpty() ) {
         return AiToolResult.ok( "The workspace contains no models.", "No models" );
      }
      final Set<String> lines = new LinkedHashSet<>();
      files.stream().limit( MAX_FILES ).forEach( file -> lines.add( "- " + describe( file ) ) );
      return AiToolResult.ok( String.join( "\n", lines ), plural( files.size(), "model file" ) );
   }

   private String describe( final WorkspaceFile file ) {
      return file.namespace() + ":" + file.version() + "/" + file.fileName()
            + ( workspaceReferenceService.isReadOnly( file.path() ) ? " (read-only)" : "" );
   }

   private static AspectModelUrn parseUrn( final String urn ) {
      return AspectModelUrn.from( urn ).toJavaOptional().orElseThrow( () -> new IllegalArgumentException( "Invalid URN: " + urn ) );
   }

   private static String unitText( final Unit unit ) {
      final StringBuilder text = new StringBuilder( unit.getName() );
      unit.getSymbol().ifPresent( symbol -> text.append( ' ' ).append( symbol ) );
      unit.getCode().ifPresent( code -> text.append( ' ' ).append( code ) );
      unit.getQuantityKinds().forEach( kind -> text.append( ' ' ).append( kind.getName() ) );
      return text.toString().toLowerCase( Locale.ROOT );
   }

   private static Optional<String> extractQuietly( final String turtle, final String urn ) {
      try {
         return TurtleElements.extract( turtle, urn );
      } catch ( final RuntimeException e ) {
         return Optional.empty();
      }
   }

   private static List<TurtleElements.Element> searchQuietly( final String turtle, final String text ) {
      try {
         return TurtleElements.search( turtle, text );
      } catch ( final RuntimeException e ) {
         return List.of();
      }
   }
}
