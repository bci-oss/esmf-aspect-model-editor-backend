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
package org.eclipse.esmf.ame.ai.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;
import org.eclipse.esmf.ame.ai.model.chat.AiChatRequest;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderConfig;
import org.eclipse.esmf.ame.ai.model.validation.AiValidationError;

import org.junit.jupiter.api.Test;

class AiPromptsTest {
   private static final String TURTLE = "@prefix : <urn:samm:org.example:1.0.0#> .\n:Movement a samm:Aspect .";
   private static final AiProviderConfig PROVIDER = new AiProviderConfig( "ollama", "llama3.2", null, null );

   private static AiChatContext context( final String turtle, final Boolean readOnly, final List<String> languages,
         final List<AiValidationError> errors ) {
      return new AiChatContext( turtle, "urn:samm:org.example:1.0.0#Movement", "org.example", "1.0.0", "Movement.ttl", "ws-1", readOnly,
            "urn:samm:org.example:1.0.0#speed", List.of( "urn:samm:org.example:1.0.0#position" ), errors, languages, null, null );
   }

   private static AiChatRequest request( final String message, final String command, final AiChatContext context ) {
      return new AiChatRequest( null, "r-1", PROVIDER, message, command, context );
   }

   @Test
   void systemPromptForbidsChangesOfReadOnlyModels() {
      final AiChatContext readOnly = context( TURTLE, true, null, null );

      assertEquals( AiPrompts.SYSTEM_PROMPT, AiPrompts.system( AiChatContext.EMPTY ) );
      assertEquals( AiPrompts.SYSTEM_PROMPT, AiPrompts.system( context( TURTLE, false, null, null ) ) );
      assertTrue( AiPrompts.system( readOnly ).startsWith( AiPrompts.SYSTEM_PROMPT ) );
      assertTrue( AiPrompts.system( readOnly ).contains( "READ-ONLY" ) );
   }

   @Test
   void noInstructionsForFreeChatOrUnknownCommands() {
      assertTrue( AiPrompts.command( null, AiChatContext.EMPTY ).isEmpty() );
      assertTrue( AiPrompts.command( " ", AiChatContext.EMPTY ).isEmpty() );
      assertTrue( AiPrompts.command( "dance", AiChatContext.EMPTY ).isEmpty() );
   }

   @Test
   void everyCommandHasInstructions() {
      for ( final String command : List.of( "explain", "describe", "translate", "generate", "review", "examples", "fix", "FIX" ) ) {
         assertTrue( AiPrompts.command( command, AiChatContext.EMPTY ).filter( text -> !text.isBlank() ).isPresent(), command );
      }
      assertTrue( AiPrompts.command( "review", AiChatContext.EMPTY ).orElseThrow().contains( "reportReview" ) );
      assertTrue( AiPrompts.command( "examples", AiChatContext.EMPTY ).orElseThrow().contains( "proposePayload" ) );
   }

   @Test
   void languageCommandsUseTheTargetLanguagesOrEnglish() {
      assertTrue( AiPrompts.command( "translate", AiChatContext.EMPTY ).orElseThrow().contains( "languages en." ) );
      assertTrue( AiPrompts.command( "describe", context( TURTLE, false, List.of(), null ) ).orElseThrow().contains( "languages en." ) );
      assertTrue( AiPrompts.command( "translate", context( TURTLE, false, List.of( "de", "fr" ), null ) ).orElseThrow()
            .contains( "languages de, fr." ) );
   }

   @Test
   void contextDescribesTheOpenModel() {
      final String block = AiPrompts.context( context( TURTLE, true, List.of( "de" ),
            List.of( new AiValidationError( "Missing unit", "urn:samm:org.example:1.0.0#speed" ),
                  new AiValidationError( "Syntax error", null ) ) ) );

      assertEquals( """
            <editor_context>
            Open file: org.example:1.0.0/Movement.ttl (read-only)
            Model URN: `urn:samm:org.example:1.0.0#Movement`
            Selected element: `urn:samm:org.example:1.0.0#speed`
            Mentioned elements: `urn:samm:org.example:1.0.0#position`
            Target languages: de
            Validation errors:
            - Missing unit (`urn:samm:org.example:1.0.0#speed`)
            - Syntax error
            </editor_context>
            <model>
            """ + TURTLE + """

            </model>

            """, block );
   }

   @Test
   void contextWithoutModel() {
      assertEquals( "<editor_context>\nNo model is open.\n</editor_context>\n\n", AiPrompts.context( AiChatContext.EMPTY ) );
   }

   @Test
   void contextOmitsLargeModelsAndLimitsValidationErrors() {
      final String turtle = "#".repeat( AiPrompts.MAX_MODEL_LENGTH + 1 );
      final List<AiValidationError> errors = IntStream.range( 0, 60 ).mapToObj( i -> new AiValidationError( "error " + i, null ) )
            .toList();

      final String block = AiPrompts.context( new AiChatContext( turtle, null, null, null, null, null, null, " ", List.of(), errors,
            null, null, null ) );

      assertFalse( block.contains( "<model>" ) );
      assertTrue( block.contains( "getOpenModel" ) );
      assertFalse( block.contains( "No model is open." ) );
      assertFalse( block.contains( "Selected element" ) );
      assertFalse( block.contains( "Mentioned elements" ) );
      assertTrue( block.contains( "- error 49\n" ) );
      assertFalse( block.contains( "- error 50\n" ) );
   }

   @Test
   void userMessageCombinesCommandContextAndText() {
      final String prompt = AiPrompts.user( request( "  Please be brief.  ", "explain", null ) );

      assertTrue( prompt.startsWith( "<command>Explain the open model" ) );
      assertTrue( prompt.contains( "</command>\n\n<editor_context>\nNo model is open.\n</editor_context>\n\n" ) );
      assertTrue( prompt.endsWith( "</editor_context>\n\nPlease be brief." ) );
   }

   @Test
   void userMessageWithoutTextAsksToPerformTheCommand() {
      assertTrue( AiPrompts.user( request( null, "fix", null ) ).endsWith( "(No further text; perform the command.)" ) );
      assertTrue( AiPrompts.user( request( " ", null, AiChatContext.EMPTY ) ).startsWith( "<editor_context>" ) );
   }

   private static AiChatRequest plain( final String message ) {
      return new AiChatRequest( null, "r1", new AiProviderConfig( "openai", null, null, null ), message, null, AiChatContext.EMPTY );
   }

   @Test
   void inlinesSmallAttachmentsAndListsLargeOnes() {
      final AiContextResolver.Resolved resolved = new AiContextResolver.Resolved( List.of(
            new AiContextResolver.Attachment( "a1", "notes \"v1\".md", "Small text", true ),
            new AiContextResolver.Attachment( "a2", "big.pdf", "x".repeat( AiPrompts.MAX_INLINE_ATTACHMENT + 1 ), false ) ),
            List.of( "gone" ), List.of(), 0, List.of() );

      final String prompt = AiPrompts.user( plain( "Summarize" ), resolved );

      assertTrue( prompt.contains( "<attachment name=\"notes v1.md\" id=\"a1\">\nSmall text\n[... truncated ...]\n</attachment>" ),
            prompt );
      assertTrue( prompt.contains( "- big.pdf (id `a2`, 2 parts)" ), prompt );
      assertTrue( prompt.contains( "1 attached file(s) are no longer available" ) );
      assertTrue( prompt.endsWith( "Summarize" ) );
      assertTrue( AiPrompts.SYSTEM_PROMPT.contains( "<attachment>" ) );
   }

   @Test
   void inlinesAttachmentsOnlyWithinTheTotalBudget() {
      final String text = "y".repeat( AiPrompts.MAX_INLINE_ATTACHMENT );
      final List<AiContextResolver.Attachment> attachments = IntStream.range( 0, 4 )
            .mapToObj( i -> new AiContextResolver.Attachment( "id" + i, "f" + i + ".txt", text, false ) ).toList();
      final String block = AiPrompts.attachments( new AiContextResolver.Resolved( attachments, List.of(), List.of(), 0, List.of() ) );
      assertEquals( 3, block.split( "<attachment ", -1 ).length - 1 );
      assertTrue( block.contains( "- f3.txt (id `id3`, 1 parts)" ) );
      assertEquals( "", AiPrompts.attachments( AiContextResolver.Resolved.EMPTY ) );
   }

   @Test
   void inlinesReadModelFilesAndListsTheOthers() {
      final AiContextResolver.Resolved resolved = new AiContextResolver.Resolved( List.of(), List.of(), List.of(
            new AiContextResolver.ModelFile( "org.example", "1.0.0", "Movement.ttl", false, "# movement" ),
            new AiContextResolver.ModelFile( "org.fleet", "1.0.0", "Vehicle.ttl", true, null ) ), 4, List.of() );

      final String block = AiPrompts.models( resolved );

      assertTrue( block.contains( "<model file=\"org.example:1.0.0/Movement.ttl\">\n# movement\n</model>" ), block );
      assertTrue( block.contains( "- org.fleet:1.0.0/Vehicle.ttl (read-only)" ) );
      assertTrue( block.contains( "readModelFile" ) );
      assertTrue( block.contains( "4 more model files" ) );
      assertEquals( "", AiPrompts.models( AiContextResolver.Resolved.EMPTY ) );
      assertTrue( AiPrompts.models( new AiContextResolver.Resolved( List.of(), List.of(), List.of(
            new AiContextResolver.ModelFile( "a", "1.0.0", "A.ttl", true, "x" ) ), 0, List.of() ) ).contains( "readOnly=\"true\"" ) );
   }

   @Test
   void countsTheAttachmentParts() {
      assertEquals( 1, AiPrompts.parts( 0 ) );
      assertEquals( 1, AiPrompts.parts( AiPrompts.ATTACHMENT_PART_LENGTH ) );
      assertEquals( 2, AiPrompts.parts( AiPrompts.ATTACHMENT_PART_LENGTH + 1 ) );
   }
}
