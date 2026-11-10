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
package org.eclipse.esmf.ame.ai.copilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderConfig;
import org.eclipse.esmf.ame.ai.provider.AiCancellation;
import org.eclipse.esmf.ame.ai.provider.AiChatListener;
import org.eclipse.esmf.ame.ai.provider.AiChatMessage;
import org.eclipse.esmf.ame.ai.provider.AiChatResult;
import org.eclipse.esmf.ame.ai.provider.AiChatTurn;
import org.eclipse.esmf.ame.ai.provider.AiProviderSettings;
import org.eclipse.esmf.ame.ai.provider.AiToolSpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CopilotProviderTest {
   private CopilotGateway gateway;
   private CopilotProvider provider;
   private final List<String> deltas = new ArrayList<>();
   private final List<String> toolCalls = new ArrayList<>();
   private final AiChatListener listener = new AiChatListener() {
      @Override
      public void onDelta( final String text ) {
         deltas.add( text );
      }

      @Override
      public String onToolCall( final String callId, final String name, final String argumentsJson ) {
         toolCalls.add( name + argumentsJson );
         return "result of " + name;
      }
   };

   @BeforeEach
   void setUp() {
      gateway = mock( CopilotGateway.class );
      provider = new CopilotProvider( gateway );
   }

   private static AiProviderSettings settings() {
      return AiProviderSettings.of( new AiProviderConfig( "copilot", "gpt-5", null, null ), null );
   }

   private static AiChatTurn turn( final List<AiChatMessage> history, final int maxToolRounds, final AiCancellation cancellation ) {
      return new AiChatTurn( "system prompt", history, "user message",
            List.of( new AiToolSpec( "listUnits", "Searches units", java.util.Map.of( "type", "object" ) ) ), maxToolRounds, cancellation );
   }

   @Test
   void needsNoKeyAndReportsAvailabilityOfTheGateway() {
      when( gateway.isAvailable() ).thenReturn( false );
      assertFalse( provider.isAvailable() );
      when( gateway.isAvailable() ).thenReturn( true );
      assertTrue( provider.isAvailable() );
      assertEquals( null, settings().apiKey() );
   }

   @Test
   void listsTheModelsOfTheGateway() {
      when( gateway.listModels() ).thenReturn( List.of( new AiModelInfo( "gpt-5", "GPT-5" ) ) );
      assertEquals( List.of( new AiModelInfo( "gpt-5", "GPT-5" ) ), provider.listModels( settings() ) );
   }

   @Test
   void passesPromptToolsAndStreamingToTheGateway() {
      final AtomicReference<CopilotGateway.ChatRequest> captured = new AtomicReference<>();
      when( gateway.chat( any() ) ).thenAnswer( invocation -> {
         final CopilotGateway.ChatRequest request = invocation.getArgument( 0 );
         captured.set( request );
         request.onDelta().accept( "Hel" );
         final String toolResult = request.toolExecutor().execute( "c1", "listUnits", "{\"query\":\"km\"}" );
         request.onDelta().accept( "lo" );
         return new AiChatResult( "Hello " + toolResult, 3L, 4L );
      } );
      final AiCancellation cancellation = new AiCancellation();

      final AiChatResult result = provider.chat( settings(), turn( List.of(), 8, cancellation ), listener );

      assertEquals( "Hello result of listUnits", result.text() );
      assertEquals( List.of( "Hel", "lo" ), deltas );
      assertEquals( List.of( "listUnits{\"query\":\"km\"}" ), toolCalls );
      final CopilotGateway.ChatRequest request = captured.get();
      assertEquals( "gpt-5", request.model() );
      assertEquals( "system prompt", request.systemPrompt() );
      assertEquals( "user message", request.prompt() );
      assertEquals( List.of( "listUnits" ), request.tools().stream().map( AiToolSpec::name ).toList() );
      assertSame( cancellation, request.cancellation() );
      assertEquals( 120_000, request.timeoutMillis() );
   }

   @Test
   void sendsTheHistoryAsPartOfThePrompt() {
      final String prompt = CopilotProvider.prompt( turn( List.of( new AiChatMessage( "user", "Q1" ), new AiChatMessage( "assistant", "A1" ) ),
            8, new AiCancellation() ) );
      assertTrue( prompt.startsWith( "<conversation_history>" ) );
      assertTrue( prompt.contains( "<user>\nQ1\n</user>" ) );
      assertTrue( prompt.contains( "<assistant>\nA1\n</assistant>" ) );
      assertTrue( prompt.endsWith( "user message" ) );
   }

   @Test
   void limitsTheNumberOfToolCalls() {
      final List<String> results = new ArrayList<>();
      when( gateway.chat( any() ) ).thenAnswer( invocation -> {
         final CopilotGateway.ChatRequest request = invocation.getArgument( 0 );
         for ( int i = 0; i < CopilotProvider.TOOL_CALLS_PER_ROUND + 2; i++ ) {
            results.add( request.toolExecutor().execute( "c" + i, "listUnits", "{}" ) );
         }
         return new AiChatResult( "", null, null );
      } );

      provider.chat( settings(), turn( List.of(), 1, new AiCancellation() ), listener );

      assertEquals( CopilotProvider.TOOL_CALLS_PER_ROUND, toolCalls.size() );
      assertEquals( CopilotProvider.TOOL_LIMIT_MESSAGE, results.getLast() );
   }

   @Test
   void refusesToolCallsAfterCancellation() {
      final AiCancellation cancellation = new AiCancellation();
      when( gateway.chat( any() ) ).thenAnswer( invocation -> {
         final CopilotGateway.ChatRequest request = invocation.getArgument( 0 );
         cancellation.cancel();
         return request.toolExecutor().execute( "c1", "listUnits", "{}" );
      } );

      assertThrows( CancellationException.class, () -> provider.chat( settings(), turn( List.of(), 8, cancellation ), listener ) );
      assertTrue( toolCalls.isEmpty() );
   }

   @Test
   void propagatesGatewayErrors() {
      when( gateway.chat( any() ) ).thenThrow( new AiException( AiErrorCode.NOT_AUTHENTICATED, "Please log in to GitHub Copilot." ) );

      final AiException exception = assertThrows( AiException.class,
            () -> provider.chat( settings(), turn( List.of(), 8, new AiCancellation() ), listener ) );
      assertEquals( AiErrorCode.NOT_AUTHENTICATED, exception.getCode() );
   }
}
