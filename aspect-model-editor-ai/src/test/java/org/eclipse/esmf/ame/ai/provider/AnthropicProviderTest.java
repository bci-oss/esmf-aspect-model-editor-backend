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
package org.eclipse.esmf.ame.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderConfig;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicProviderTest {
   private static final String KEY = "sk-ant-test-key-4711";
   private static final String MESSAGES = "/v1/messages";
   private static final String TOOL_CALL_STREAM = """
         event: message_start
         data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[],"model":"claude-test","stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":1}}}

         event: content_block_start
         data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_1","name":"listUnits","input":{}}}

         event: content_block_delta
         data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\\"query\\": \\"km/h\\"}"}}

         event: content_block_stop
         data: {"type":"content_block_stop","index":0}

         event: message_delta
         data: {"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"output_tokens":5}}

         event: message_stop
         data: {"type":"message_stop"}

         """;
   private static final String TEXT_STREAM = """
         event: message_start
         data: {"type":"message_start","message":{"id":"msg_2","type":"message","role":"assistant","content":[],"model":"claude-test","stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":20,"output_tokens":1}}}

         event: content_block_start
         data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

         event: content_block_delta
         data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Use "}}

         event: content_block_delta
         data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"km/h."}}

         event: content_block_stop
         data: {"type":"content_block_stop","index":0}

         event: message_delta
         data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":3}}

         event: message_stop
         data: {"type":"message_stop"}

         """;

   private MockAiServer server;
   private final AnthropicProvider provider = new AnthropicProvider();

   @BeforeEach
   void setUp() throws Exception {
      server = new MockAiServer();
   }

   @AfterEach
   void tearDown() {
      server.close();
   }

   private AiProviderSettings settings() {
      return AiProviderSettings.of( new AiProviderConfig( "anthropic", "claude-test", server.baseUrl(), null ), KEY );
   }

   @Test
   void streamsTextAndExecutesToolCalls() {
      server.enqueue( MESSAGES, MockAiServer.Response.sse( TOOL_CALL_STREAM ) )
            .enqueue( MESSAGES, MockAiServer.Response.sse( TEXT_STREAM ) );
      final RecordingListener listener = new RecordingListener();

      final AiChatResult result = provider.chat( settings(), RecordingListener.turn( 8 ), listener );

      assertEquals( "Use km/h.", result.text() );
      assertEquals( "Use km/h.", listener.text() );
      assertEquals( 1, listener.toolCalls.size() );
      assertEquals( "toolu_1", listener.toolCalls.getFirst().callId() );
      assertEquals( "listUnits", listener.toolCalls.getFirst().name() );
      assertTrue( listener.toolCalls.getFirst().argumentsJson().contains( "km/h" ) );

      assertEquals( 2, server.requests().size() );
      final MockAiServer.Request first = server.requests().getFirst();
      assertEquals( KEY, first.header( "x-api-key" ) );
      assertTrue( first.body().contains( "\"claude-test\"" ) );
      assertTrue( first.body().contains( "You are a test." ) );
      assertTrue( server.requests().get( 1 ).body().contains( "kilometrePerHour (km/h)" ) );
   }

   @Test
   void listsModels() {
      server.enqueue( "/v1/models", MockAiServer.Response.json( 200, """
            {"data":[{"type":"model","id":"claude-a","display_name":"Claude A","created_at":"2025-01-01T00:00:00Z"}],
            "has_more":false,"first_id":"claude-a","last_id":"claude-a"}""" ) );

      final List<AiModelInfo> models = provider.listModels( settings() );

      assertEquals( List.of( new AiModelInfo( "claude-a", "Claude A" ) ), models );
      assertEquals( KEY, server.requests().getFirst().header( "x-api-key" ) );
   }

   @Test
   void listsModelsWithTheReadTimeoutOfChats() {
      server.enqueue( "/v1/models", MockAiServer.Response.json( 200, "{\"data\":[],\"has_more\":false}" ) );
      final List<JdkHttpClientBuilder> builders = new CopyOnWriteArrayList<>();
      final AnthropicProvider recording = new AnthropicProvider() {
         @Override
         protected JdkHttpClientBuilder httpClientBuilder( final AiProviderSettings settings ) {
            final JdkHttpClientBuilder builder = super.httpClientBuilder( settings );
            builders.add( builder );
            return builder;
         }
      };

      recording.listModels( settings() );

      assertEquals( 1, builders.size() );
      assertEquals( AiHttpClients.READ_TIMEOUT, builders.getFirst().readTimeout() );
   }

   @Test
   void mapsUnauthorizedToInvalidKey() {
      final String unauthorized = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
      server.enqueue( "/v1/models", MockAiServer.Response.json( 401, unauthorized ) )
            .enqueue( MESSAGES, MockAiServer.Response.json( 401, unauthorized ) );

      final AiException listError = assertThrows( AiException.class, () -> provider.listModels( settings() ) );
      assertEquals( AiErrorCode.INVALID_KEY, listError.getCode() );

      final AiException chatError = assertThrows( AiException.class,
            () -> provider.chat( settings(), RecordingListener.turn( 8 ), new RecordingListener() ) );
      assertEquals( AiErrorCode.INVALID_KEY, chatError.getCode() );
      assertFalse( chatError.getMessage().contains( KEY ) );
   }
}
