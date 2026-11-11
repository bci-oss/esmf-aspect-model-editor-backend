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
import static org.junit.jupiter.api.Assertions.assertNull;
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

class OpenAiCompatibleProviderTest {
   private static final String KEY = "sk-test-key-0815";
   private static final String COMPLETIONS = "/v1/chat/completions";
   private static final String TOOL_CALL_STREAM = """
         data: {"id":"c1","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"listUnits","arguments":""}}]},"finish_reason":null}]}

         data: {"id":"c1","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"query\\":\\"km/h\\"}"}}]},"finish_reason":null}]}

         data: {"id":"c1","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}

         data: [DONE]

         """;
   private static final String TEXT_STREAM = """
         data: {"id":"c2","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Use "},"finish_reason":null}]}

         data: {"id":"c2","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{"content":"km/h."},"finish_reason":null}]}

         data: {"id":"c2","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":20,"completion_tokens":3,"total_tokens":23}}

         data: [DONE]

         """;
   private static final String UNAUTHORIZED = """
         {"error":{"message":"Incorrect API key provided: sk-test-key-0815","type":"invalid_request_error","code":"invalid_api_key"}}""";

   private MockAiServer server;
   private final OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider();

   @BeforeEach
   void setUp() throws Exception {
      server = new MockAiServer();
   }

   @AfterEach
   void tearDown() {
      server.close();
   }

   private AiProviderSettings settings( final String model ) {
      return AiProviderSettings.of( new AiProviderConfig( "openai-compatible", model, server.baseUrl(), null ), KEY );
   }

   @Test
   void streamsTextAndExecutesToolCalls() {
      server.enqueue( COMPLETIONS, MockAiServer.Response.sse( TOOL_CALL_STREAM ) )
            .enqueue( COMPLETIONS, MockAiServer.Response.sse( TEXT_STREAM ) );
      final RecordingListener listener = new RecordingListener();

      final AiChatResult result = provider.chat( settings( "test-model" ), RecordingListener.turn( 8 ), listener );

      assertEquals( "Use km/h.", result.text() );
      assertEquals( "Use km/h.", listener.text() );
      assertEquals( List.of( new RecordingListener.ToolCall( "call_1", "listUnits", "{\"query\":\"km/h\"}" ) ), listener.toolCalls );
      assertEquals( 30L, result.inputTokens() );
      assertEquals( 8L, result.outputTokens() );

      assertEquals( 2, server.requests().size() );
      final MockAiServer.Request first = server.requests().getFirst();
      assertEquals( "Bearer " + KEY, first.header( "Authorization" ) );
      assertTrue( first.body().replaceAll( "\\s", "" ).contains( "\"stream\":true" ) );
      assertTrue( first.body().contains( "\"listUnits\"" ) );
      assertTrue( first.body().contains( "Earlier answer" ) );
      final MockAiServer.Request second = server.requests().get( 1 );
      assertTrue( second.body().contains( "kilometrePerHour (km/h)" ), "The tool result is sent back to the model" );
   }

   @Test
   void offersNoToolsAfterTheMaximumNumberOfRounds() {
      server.enqueue( COMPLETIONS, MockAiServer.Response.sse( TOOL_CALL_STREAM ) )
            .enqueue( COMPLETIONS, MockAiServer.Response.sse( TEXT_STREAM ) );
      final RecordingListener listener = new RecordingListener();

      provider.chat( settings( "test-model" ), RecordingListener.turn( 1 ), listener );

      assertEquals( 1, listener.toolCalls.size() );
      assertTrue( server.requests().getFirst().body().contains( "\"tools\"" ) );
      assertFalse( server.requests().get( 1 ).body().contains( "\"tools\"" ) );
   }

   @Test
   void listsModels() {
      server.enqueue( "/v1/models", MockAiServer.Response.json( 200, """
            {"object":"list","data":[{"id":"model-a","object":"model","created":1,"owned_by":"me"},
            {"id":"model-b","object":"model","created":1,"owned_by":"me"}]}""" ) );

      final List<AiModelInfo> models = provider.listModels( settings( null ) );

      assertEquals( List.of( "model-a", "model-b" ), models.stream().map( AiModelInfo::id ).toList() );
      assertEquals( "Bearer " + KEY, server.requests().getFirst().header( "Authorization" ) );
   }

   @Test
   void listsModelsWithTheReadTimeoutOfChats() {
      server.enqueue( "/v1/models", MockAiServer.Response.json( 200, "{\"object\":\"list\",\"data\":[]}" ) );
      final List<JdkHttpClientBuilder> builders = new CopyOnWriteArrayList<>();
      final OpenAiCompatibleProvider recording = new OpenAiCompatibleProvider() {
         @Override
         protected JdkHttpClientBuilder httpClientBuilder( final AiProviderSettings settings ) {
            final JdkHttpClientBuilder builder = super.httpClientBuilder( settings );
            builders.add( builder );
            return builder;
         }
      };

      recording.listModels( settings( null ) );

      assertEquals( 1, builders.size() );
      assertEquals( AiHttpClients.CONNECT_TIMEOUT, builders.getFirst().connectTimeout() );
      assertEquals( AiHttpClients.READ_TIMEOUT, builders.getFirst().readTimeout() );
   }

   @Test
   void usesTheFirstModelWhenNoneIsConfigured() {
      server.enqueue( "/v1/models", MockAiServer.Response.json( 200, """
                  {"object":"list","data":[{"id":"local-model","object":"model","created":1,"owned_by":"me"}]}""" ) )
            .enqueue( COMPLETIONS, MockAiServer.Response.sse( TEXT_STREAM ) );

      provider.chat( settings( null ), RecordingListener.turn( 8 ), new RecordingListener() );

      assertTrue( server.requests().get( 1 ).body().replaceAll( "\\s", "" ).contains( "\"model\":\"local-model\"" ) );
   }

   @Test
   void mapsUnauthorizedToInvalidKeyWithoutLeakingTheKey() {
      server.enqueue( "/v1/models", MockAiServer.Response.json( 401, UNAUTHORIZED ) )
            .enqueue( COMPLETIONS, MockAiServer.Response.json( 401, UNAUTHORIZED ) );

      final AiException listError = assertThrows( AiException.class, () -> provider.listModels( settings( null ) ) );
      assertEquals( AiErrorCode.INVALID_KEY, listError.getCode() );
      assertFalse( listError.getMessage().contains( KEY ) );

      final AiException chatError = assertThrows( AiException.class,
            () -> provider.chat( settings( "test-model" ), RecordingListener.turn( 8 ), new RecordingListener() ) );
      assertEquals( AiErrorCode.INVALID_KEY, chatError.getCode() );
      assertFalse( chatError.getMessage().contains( KEY ) );
   }

   @Test
   void mapsRateLimitsAndUnreachableServers() {
      server.enqueue( COMPLETIONS, MockAiServer.Response.json( 429, "{\"error\":{\"message\":\"Rate limit\"}}" ) );
      final AiException quota = assertThrows( AiException.class,
            () -> provider.chat( settings( "test-model" ), RecordingListener.turn( 8 ), new RecordingListener() ) );
      assertEquals( AiErrorCode.QUOTA_EXCEEDED, quota.getCode() );

      final String baseUrl = server.baseUrl();
      server.close();
      final AiException offline = assertThrows( AiException.class, () -> provider.listModels(
            AiProviderSettings.of( new AiProviderConfig( "openai-compatible", null, baseUrl, null ), null ) ) );
      assertEquals( AiErrorCode.OFFLINE, offline.getCode() );
   }

   @Test
   void sendsTheAzureKeyAndApiVersion() {
      server.enqueue( "/v1/models", MockAiServer.Response.json( 200, "{\"object\":\"list\",\"data\":[]}" ) );
      final AiProviderSettings azure = AiProviderSettings.of(
            new AiProviderConfig( "azure-openai", "deployment", server.baseUrl(), "2025-04-01-preview" ), KEY );

      provider.listModels( azure );

      final MockAiServer.Request request = server.requests().getFirst();
      assertEquals( KEY, request.header( "api-key" ) );
      assertNull( request.header( "Authorization" ) );
   }
}
