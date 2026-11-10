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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.esmf.ame.ai.TestDirectories;
import org.eclipse.esmf.ame.ai.attachment.AiAttachmentStore;
import org.eclipse.esmf.ame.ai.chat.AiPrompts;
import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;
import org.eclipse.esmf.ame.ai.model.proposal.AiProposal;
import org.eclipse.esmf.ame.ai.model.validation.AiValidationError;
import org.eclipse.esmf.ame.ai.model.validation.AiValidationResult;
import org.eclipse.esmf.ame.ai.provider.AiToolSpec;
import org.eclipse.esmf.ame.services.ModelService;
import org.eclipse.esmf.ame.services.models.AspectModelResult;
import org.eclipse.esmf.ame.services.workspace.ElementUsageService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService.WorkspaceFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiToolsTest {
   static final String TURTLE = """
         @prefix samm: <urn:samm:org.eclipse.esmf.samm:meta-model:2.2.0#> .
         @prefix samm-c: <urn:samm:org.eclipse.esmf.samm:characteristic:2.2.0#> .
         @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
         @prefix unit: <urn:samm:org.eclipse.esmf.samm:unit:2.2.0#> .
         @prefix : <urn:samm:org.example:1.0.0#> .

         :Movement a samm:Aspect ;
            samm:preferredName "Movement"@en ;
            samm:description "Aspect for movement information"@en ;
            samm:properties ( :speed ) ;
            samm:operations ( ) .

         :speed a samm:Property ;
            samm:preferredName "speed"@en ;
            samm:description "Scalar speed of a vehicle."@en ;
            samm:characteristic :Speed .

         :Speed a samm-c:Measurement ;
            samm:dataType xsd:float ;
            samm-c:unit unit:kilometrePerHour .
         """;
   private static final AiValidationResult VALID = new AiValidationResult( true, List.of() );
   private static final AiValidationResult INVALID = new AiValidationResult( false,
         List.of( new AiValidationError( "Property without characteristic", "urn:samm:org.example:1.0.0#speed" ) ) );

   private AiModelValidator validator;
   private ModelService modelService;
   private WorkspaceReferenceService workspaceReferenceService;
   private AiTools tools;
   private final List<AiProposal> emitted = new ArrayList<>();

   @BeforeEach
   void setUp() {
      validator = mock( AiModelValidator.class );
      modelService = mock( ModelService.class );
      workspaceReferenceService = mock( WorkspaceReferenceService.class );
      when( workspaceReferenceService.listWorkspaceFiles() ).thenReturn( List.of() );
      tools = new AiTools( validator, modelService, mock( ElementUsageService.class ), workspaceReferenceService,
            new AiAttachmentStore() );
   }

   private static AiChatContext context( final boolean readOnly ) {
      return new AiChatContext( TURTLE, "urn:samm:org.example:1.0.0#Movement", "org.example", "1.0.0", "Movement.ttl", null, readOnly,
            null, null, null, List.of( "en" ), null, null );
   }

   private AiToolSession session( final AiChatContext context ) {
      return new AiToolSession( context, emitted::add );
   }

   private static List<String> names( final List<AiToolSpec> specs ) {
      return specs.stream().map( AiToolSpec::name ).toList();
   }

   @Test
   void offersModelToolsOnlyWithOpenModelAndChangeToolsOnlyWhenWritable() {
      final List<String> writable = names( tools.specs( context( false ) ) );
      assertTrue( writable.containsAll( List.of( AiTools.GET_OPEN_MODEL, AiTools.PROPOSE_MODEL_CHANGE, AiTools.PROPOSE_RENAME,
            AiTools.GET_VALIDATION_ERRORS, AiTools.GENERATE_SAMPLE_PAYLOAD ) ) );

      final List<String> readOnly = names( tools.specs( context( true ) ) );
      assertTrue( readOnly.contains( AiTools.GET_OPEN_MODEL ) );
      assertFalse( readOnly.contains( AiTools.PROPOSE_MODEL_CHANGE ) );
      assertFalse( readOnly.contains( AiTools.PROPOSE_RENAME ) );

      final List<String> noModel = names( tools.specs( AiChatContext.EMPTY ) );
      assertFalse( noModel.contains( AiTools.GET_OPEN_MODEL ) );
      assertFalse( noModel.contains( AiTools.PROPOSE_MODEL_CHANGE ) );
      assertTrue( noModel.containsAll( List.of( AiTools.PROPOSE_NEW_MODEL, AiTools.SEARCH_ELEMENTS, AiTools.LIST_UNITS ) ) );
      tools.specs( context( false ) ).forEach( spec -> assertEquals( "object", spec.parameters().get( "type" ) ) );
   }

   @Test
   void returnsOpenModelAsData() {
      final AiToolResult result = tools.execute( session( context( false ) ), AiTools.GET_OPEN_MODEL, "{}" );
      assertFalse( result.failed() );
      assertTrue( result.output().contains( "<model" ) );
      assertTrue( result.output().contains( ":Movement a samm:Aspect" ) );
   }

   @Test
   void returnsElementOfOpenModelAndFallsBackToWorkspace() {
      final AiToolResult speed = tools.execute( session( context( false ) ), AiTools.GET_ELEMENT,
            "{\"urn\":\"urn:samm:org.example:1.0.0#Speed\"}" );
      assertFalse( speed.failed() );
      assertTrue( speed.output().contains( "kilometrePerHour" ) );
      assertFalse( speed.output().contains( "samm:Aspect" ) );

      when( modelService.getUnresolvedModel( any() ) ).thenReturn(
            Optional.of( new AspectModelResult( Optional.of( "Other.ttl" ), TURTLE.replace( "org.example:1.0.0", "org.other:2.0.0" ),
                  Optional.empty() ) ) );
      final AiToolResult other = tools.execute( session( AiChatContext.EMPTY ), AiTools.GET_ELEMENT,
            "{\"urn\":\"urn:samm:org.other:2.0.0#speed\"}" );
      assertFalse( other.failed(), other.output() );
      assertTrue( other.output().contains( "Scalar speed" ) );

      final AiToolResult invalid = tools.execute( session( AiChatContext.EMPTY ), AiTools.GET_ELEMENT, "{\"urn\":\"no urn\"}" );
      assertTrue( invalid.failed() );
   }

   @Test
   void searchesElements() {
      final AiToolResult result = tools.execute( session( context( false ) ), AiTools.SEARCH_ELEMENTS, "{\"text\":\"vehicle\"}" );
      assertFalse( result.failed() );
      assertTrue( result.output().contains( "urn:samm:org.example:1.0.0#speed" ) );
      assertFalse( result.output().contains( "#Movement" ) );
   }

   @Test
   void listsUnitsAndCharacteristics() {
      final AiToolResult units = tools.execute( session( AiChatContext.EMPTY ), AiTools.LIST_UNITS, "{\"query\":\"kilometrePerHour\"}" );
      assertTrue( units.output().contains( "unit:kilometrePerHour" ), units.output() );
      final AiToolResult characteristics = tools.execute( session( AiChatContext.EMPTY ), AiTools.LIST_CHARACTERISTICS,
            "{\"query\":\"Measurement\"}" );
      assertTrue( characteristics.output().contains( "Measurement" ) );
   }

   @Test
   void rejectsInvalidArguments() {
      assertTrue( tools.execute( session( AiChatContext.EMPTY ), AiTools.GET_ELEMENT, "{not json" ).failed() );
      assertTrue( tools.execute( session( AiChatContext.EMPTY ), AiTools.GET_ELEMENT, "{}" ).failed() );
      assertTrue( tools.execute( session( AiChatContext.EMPTY ), "deleteEverything", "{}" ).failed() );
   }

   @Test
   void proposesValidModelChange() {
      when( validator.validate( anyString(), any(), any(), any() ) ).thenReturn( VALID );
      final AiToolResult result = tools.execute( session( context( false ) ), AiTools.PROPOSE_MODEL_CHANGE,
            "{\"turtle\":\"" + escape( TURTLE ) + "\",\"title\":\"Add speed\",\"summary\":\"Adds speed\"}" );

      assertFalse( result.failed() );
      assertEquals( 1, emitted.size() );
      final AiProposal proposal = emitted.getFirst();
      assertEquals( AiProposal.MODEL_CHANGE, proposal.kind() );
      assertEquals( "Add speed", proposal.title() );
      assertEquals( TURTLE, proposal.turtle() );
      assertEquals( TURTLE, proposal.originalTurtle() );
      assertEquals( "org.example", proposal.namespace() );
      assertEquals( "Movement.ttl", proposal.fileName() );
      assertTrue( proposal.valid() );
      verify( validator ).validate( TURTLE, "org.example", "1.0.0", "Movement.ttl" );
   }

   @Test
   void returnsValidationErrorsForSelfCorrectionTwiceThenProposesInvalidModel() {
      when( validator.validate( anyString(), any(), any(), any() ) ).thenReturn( INVALID );
      final AiToolSession session = session( context( false ) );
      final String args = "{\"turtle\":\"" + escape( TURTLE ) + "\",\"title\":\"Change\"}";

      for ( int attempt = 1; attempt <= AiTools.MAX_SELF_CORRECTIONS; attempt++ ) {
         final AiToolResult retry = tools.execute( session, AiTools.PROPOSE_MODEL_CHANGE, args );
         assertTrue( retry.failed() );
         assertTrue( retry.output().contains( "Property without characteristic" ) );
         assertTrue( emitted.isEmpty() );
      }
      final AiToolResult last = tools.execute( session, AiTools.PROPOSE_MODEL_CHANGE, args );
      assertFalse( last.failed() );
      assertEquals( 1, emitted.size() );
      assertFalse( emitted.getFirst().valid() );
      assertEquals( 1, emitted.getFirst().validationErrors().size() );
   }

   @Test
   void validModelResetsSelfCorrectionCounter() {
      final AiToolSession session = session( context( false ) );
      final String args = "{\"turtle\":\"" + escape( TURTLE ) + "\"}";
      when( validator.validate( anyString(), any(), any(), any() ) ).thenReturn( INVALID, INVALID, VALID, INVALID );
      assertTrue( tools.execute( session, AiTools.PROPOSE_MODEL_CHANGE, args ).failed() );
      assertTrue( tools.execute( session, AiTools.PROPOSE_MODEL_CHANGE, args ).failed() );
      assertFalse( tools.execute( session, AiTools.PROPOSE_MODEL_CHANGE, args ).failed() );
      assertTrue( tools.execute( session, AiTools.PROPOSE_MODEL_CHANGE, args ).failed() );
      assertEquals( 1, emitted.size() );
   }

   @Test
   void refusesChangesOfReadOnlyModels() {
      final AiToolResult change = tools.execute( session( context( true ) ), AiTools.PROPOSE_MODEL_CHANGE,
            "{\"turtle\":\"" + escape( TURTLE ) + "\"}" );
      final AiToolResult rename = tools.execute( session( context( true ) ), AiTools.PROPOSE_RENAME,
            "{\"urn\":\"urn:samm:org.example:1.0.0#speed\",\"newName\":\"velocity\"}" );

      assertTrue( change.failed() );
      assertEquals( "read_only", change.summary() );
      assertTrue( change.output().startsWith( "read_only" ) );
      assertTrue( rename.failed() );
      assertTrue( emitted.isEmpty() );
      verify( validator, never() ).validate( anyString(), any(), any(), any() );
   }

   @Test
   void proposesNewModelWithTurtleExtension() {
      when( validator.validate( anyString(), any(), any(), any() ) ).thenReturn( VALID );
      final AiToolResult result = tools.execute( session( context( true ) ), AiTools.PROPOSE_NEW_MODEL,
            "{\"turtle\":\"" + escape( TURTLE ) + "\",\"namespace\":\"org.example\",\"version\":\"1.0.0\",\"fileName\":\"Copy\","
                  + "\"title\":\"Copy\"}" );
      assertFalse( result.failed() );
      assertEquals( AiProposal.NEW_MODEL, emitted.getFirst().kind() );
      assertEquals( "Copy.ttl", emitted.getFirst().fileName() );
      assertNull( emitted.getFirst().originalTurtle() );
   }

   @Test
   void proposesRename() {
      final AiToolResult result = tools.execute( session( context( false ) ), AiTools.PROPOSE_RENAME,
            "{\"urn\":\"urn:samm:org.example:1.0.0#speed\",\"newName\":\"velocity\"}" );
      assertFalse( result.failed() );
      final AiProposal proposal = emitted.getFirst();
      assertEquals( AiProposal.REFACTORING, proposal.kind() );
      assertEquals( "renameElement", proposal.refactoring().kind() );
      assertEquals( "velocity", proposal.refactoring().newName() );

      assertTrue( tools.execute( session( context( false ) ), AiTools.PROPOSE_RENAME,
            "{\"urn\":\"urn:samm:org.example:1.0.0#speed\",\"newName\":\"../evil\"}" ).failed() );
   }

   @Test
   void proposesPayloadsAndRejectsInvalidJson() {
      final AiToolResult result = tools.execute( session( context( false ) ), AiTools.PROPOSE_PAYLOAD,
            "{\"variants\":[{\"name\":\"valid\",\"json\":\"{\\\"speed\\\":12.5}\"},{\"name\":\"object\",\"json\":{\"speed\":0}}]}" );
      assertFalse( result.failed(), result.output() );
      final AiProposal proposal = emitted.getFirst();
      assertEquals( AiProposal.PAYLOAD, proposal.kind() );
      assertEquals( 2, proposal.payload().size() );
      assertTrue( proposal.payload().getFirst().json().contains( "12.5" ) );

      assertTrue( tools.execute( session( context( false ) ), AiTools.PROPOSE_PAYLOAD,
            "{\"variants\":[{\"name\":\"broken\",\"json\":\"{speed:\"}]}" ).failed() );
      assertEquals( 1, emitted.size() );
   }

   @Test
   void reportsReviewFindings() {
      final AiToolResult result = tools.execute( session( context( true ) ), AiTools.REPORT_REVIEW,
            "{\"title\":\"Review\",\"findings\":[{\"severity\":\"warning\",\"category\":\"description\",\"message\":\"Add a description\","
                  + "\"elementUrn\":\"urn:samm:org.example:1.0.0#Speed\",\"fixable\":true},"
                  + "{\"severity\":\"bogus\",\"category\":\"bogus\",\"message\":\"Other\"},{\"message\":\"\"}]}" );
      assertFalse( result.failed() );
      final AiProposal proposal = emitted.getFirst();
      assertEquals( AiProposal.REVIEW, proposal.kind() );
      assertEquals( 2, proposal.findings().size() );
      assertEquals( "f1", proposal.findings().get( 0 ).id() );
      assertTrue( proposal.findings().get( 0 ).fixable() );
      assertEquals( "info", proposal.findings().get( 1 ).severity() );
      assertEquals( "other", proposal.findings().get( 1 ).category() );
   }

   @Test
   void usesValidationErrorsOfContext() {
      final AiChatContext context = new AiChatContext( TURTLE, null, "org.example", "1.0.0", "Movement.ttl", null, false, null, null,
            List.of( new AiValidationError( "Broken", "urn:samm:org.example:1.0.0#speed" ) ), null, null, null );
      final AiToolResult result = tools.execute( session( context ), AiTools.GET_VALIDATION_ERRORS, "{}" );
      assertTrue( result.output().contains( "Broken" ) );
      verify( validator, never() ).validate( anyString(), any(), any(), any() );
   }

   static String escape( final String text ) {
      return text.replace( "\\", "\\\\" ).replace( "\"", "\\\"" ).replace( "\n", "\\n" );
   }

   @Test
   void offersReadAttachmentOnlyWithAttachments() {
      assertFalse( names( tools.specs( context( false ) ) ).contains( AiTools.READ_ATTACHMENT ) );
      assertTrue( names( tools.specs( context( false ) ) ).contains( AiTools.READ_MODEL_FILE ) );
      final AiChatContext withAttachment = new AiChatContext( null, null, null, null, null, null, null, null, null, null, null,
            List.of( "a1" ), null );
      assertTrue( names( tools.specs( withAttachment ) ).contains( AiTools.READ_ATTACHMENT ) );
   }

   @Test
   void readsAttachmentsPartByPart() {
      final AiAttachmentStore store = new AiAttachmentStore();
      tools = new AiTools( validator, modelService, mock( ElementUsageService.class ), workspaceReferenceService, store );
      final String text = "a".repeat( AiPrompts.ATTACHMENT_PART_LENGTH ) + "tail";
      final String id = store.add( "spec.txt", text.getBytes( StandardCharsets.UTF_8 ) ).id();

      final AiToolResult first = tools.execute( session( context( false ) ), AiTools.READ_ATTACHMENT, "{\"id\":\"" + id + "\"}" );
      assertFalse( first.failed() );
      assertTrue( first.output().startsWith( "<attachment name=\"spec.txt\" part=\"1/2\">" ) );
      assertFalse( first.output().contains( "tail" ) );
      assertEquals( "Read spec.txt (part 1/2)", first.summary() );

      final AiToolResult second = tools.execute( session( context( false ) ), AiTools.READ_ATTACHMENT,
            "{\"id\":\"" + id + "\",\"part\":\"2\"}" );
      assertTrue( second.output().contains( "\ntail\n</attachment>" ), second.output() );

      assertTrue( tools.execute( session( context( false ) ), AiTools.READ_ATTACHMENT, "{\"id\":\"" + id + "\",\"part\":3}" )
            .failed() );
      assertTrue( tools.execute( session( context( false ) ), AiTools.READ_ATTACHMENT, "{\"id\":\"" + id + "\",\"part\":\"x\"}" )
            .failed() );
      final AiToolResult missing = tools.execute( session( context( false ) ), AiTools.READ_ATTACHMENT, "{\"id\":\"gone\"}" );
      assertTrue( missing.failed() );
      assertTrue( missing.output().contains( "attach it again" ) );
      assertTrue( tools.execute( session( context( false ) ), AiTools.READ_ATTACHMENT, "{}" ).failed() );
   }

   @Test
   void readsModelFilesOfTheWorkspace() throws Exception {
      final Path directory = TestDirectories.create( "read-model" );
      try {
         final Path file = Files.writeString( directory.resolve( "Movement.ttl" ), "# movement" );
         when( workspaceReferenceService.listWorkspaceFiles() ).thenReturn( List.of(
               new WorkspaceFile( file, "org.example", "1.0.0", "Movement.ttl", directory ),
               new WorkspaceFile( directory.resolve( "Gone.ttl" ), "org.example", "1.0.0", "Gone.ttl", directory ) ) );

         final AiToolResult result = tools.execute( session( context( false ) ), AiTools.READ_MODEL_FILE,
               "{\"namespace\":\"org.example\",\"version\":\"1.0.0\",\"fileName\":\"Movement.ttl\"}" );
         assertFalse( result.failed() );
         assertTrue( result.output().contains( "<model file=\"Movement.ttl\">\n# movement" ) );

         assertTrue( tools.execute( session( context( false ) ), AiTools.READ_MODEL_FILE,
               "{\"namespace\":\"org.example\",\"version\":\"2.0.0\",\"fileName\":\"Movement.ttl\"}" ).failed() );
         assertTrue( tools.execute( session( context( false ) ), AiTools.READ_MODEL_FILE,
               "{\"namespace\":\"org.example\",\"version\":\"1.0.0\",\"fileName\":\"Gone.ttl\"}" ).failed() );
         assertTrue( tools.execute( session( context( false ) ), AiTools.READ_MODEL_FILE, "{\"namespace\":\"org.example\"}" )
               .failed() );
      } finally {
         TestDirectories.delete( directory );
      }
   }
}
