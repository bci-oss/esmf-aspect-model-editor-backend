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
package org.eclipse.esmf.ame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.attachment.AttachmentTextExtractor;
import org.eclipse.esmf.ame.ai.copilot.CopilotGateway;
import org.eclipse.esmf.ame.ai.model.copilot.CopilotDeviceLogin;
import org.eclipse.esmf.ame.ai.model.copilot.CopilotStatus;
import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;
import org.eclipse.esmf.ame.ai.provider.AiChatListener;
import org.eclipse.esmf.ame.ai.provider.AiChatResult;
import org.eclipse.esmf.ame.ai.provider.AiChatTurn;
import org.eclipse.esmf.ame.ai.provider.AiProvider;
import org.eclipse.esmf.ame.ai.provider.AiProviderFactory;
import org.eclipse.esmf.ame.ai.provider.AiProviderKind;
import org.eclipse.esmf.ame.ai.provider.AiProviderSettings;
import org.eclipse.esmf.ame.ai.tools.AiTools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Property;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Checks the AI endpoints over HTTP with a scripted AI provider instead of a real one. The endpoints are served by several
 * controllers ({@code AiProviderController}, {@code AiChatController}, {@code AiConversationController},
 * {@code AiAttachmentController}, {@code AiProposalController} and {@code AiCopilotController}) below {@code /ame/api/ai}.
 */
@MicronautTest
@Property( name = "ame.ai.conversations-dir", value = AiControllerIntegrationTest.CONVERSATIONS )
class AiControllerIntegrationTest {
   static final String CONVERSATIONS = "target/ai-it-conversations";
   private static final String KEY = "sk-test-secret";
   private static final ObjectMapper JSON = new ObjectMapper();

   @Inject
   EmbeddedServer server;

   private final HttpClient http = HttpClient.newHttpClient();

   @MockBean( AiProviderFactory.class )
   AiProviderFactory providerFactory() {
      return new AiProviderFactory( List.of( new ScriptedProvider() ), false );
   }

   @MockBean( CopilotGateway.class )
   CopilotGateway copilotGateway() {
      return new UnavailableCopilot();
   }

   @AfterAll
   static void deleteConversations() throws IOException {
      final Path directory = Path.of( CONVERSATIONS );
      if ( Files.exists( directory ) ) {
         try ( final Stream<Path> paths = Files.walk( directory ) ) {
            paths.sorted( Comparator.reverseOrder() ).forEach( path -> path.toFile().delete() );
         }
      }
   }

   @Test
   void listsTheProviders() throws Exception {
      final HttpResponse<String> response = send( "GET", "/ame/api/ai/providers", null, null );
      assertEquals( 200, response.statusCode() );
      final JsonNode providers = JSON.readTree( response.body() );
      assertTrue( providers.isArray() && !providers.isEmpty() );
      final List<String> ids = new ArrayList<>();
      providers.forEach( provider -> ids.add( provider.get( "kind" ).asText() ) );
      assertTrue( ids.containsAll( List.of( "openai", "anthropic", "ollama", "copilot" ) ), ids.toString() );
   }

   @Test
   void streamsConversationDeltaProposalAndDoneAndStoresTheConversation() throws Exception {
      final String requestId = UUID.randomUUID().toString();
      final HttpResponse<String> response = send( "POST", "/ame/api/ai/chat", chatRequest( requestId ), KEY );
      assertEquals( 200, response.statusCode() );
      assertTrue( response.headers().firstValue( "content-type" ).orElseThrow().startsWith( "text/event-stream" ) );

      final List<String[]> events = parseEvents( response.body() );
      final List<String> names = events.stream().map( event -> event[0] ).toList();
      assertEquals( "conversation", names.getFirst() );
      assertEquals( "done", names.getLast() );
      assertTrue( names.indexOf( "delta" ) < names.indexOf( "proposal" ), names.toString() );
      assertFalse( response.body().contains( KEY ), "the API key must never be echoed" );
      final JsonNode proposal = JSON.readTree( events.get( names.indexOf( "proposal" ) )[1] );
      assertEquals( "payload", proposal.get( "kind" ).asText() );
      final String conversationId = JSON.readTree( events.getFirst()[1] ).get( "id" ).asText();

      final HttpResponse<String> list = send( "GET", "/ame/api/ai/conversations", null, null );
      assertEquals( 200, list.statusCode() );
      assertTrue( list.body().contains( conversationId ) );

      final HttpResponse<String> conversation = send( "GET", "/ame/api/ai/conversations/" + conversationId, null, null );
      assertEquals( 200, conversation.statusCode() );
      assertEquals( 2, JSON.readTree( conversation.body() ).get( "messages" ).size() );
      assertFalse( Files.walk( Path.of( CONVERSATIONS ) ).filter( Files::isRegularFile ).anyMatch( file -> contains( file, KEY ) ) );

      final HttpResponse<String> renamed = send( "PATCH", "/ame/api/ai/conversations/" + conversationId, "{\"title\":\"Renamed\"}",
            null );
      assertEquals( 200, renamed.statusCode() );
      assertEquals( "Renamed", JSON.readTree( renamed.body() ).get( "title" ).asText() );

      assertEquals( 204, send( "DELETE", "/ame/api/ai/conversations/" + conversationId, null, null ).statusCode() );
      assertEquals( 404, send( "GET", "/ame/api/ai/conversations/" + conversationId, null, null ).statusCode() );
   }

   @Test
   void rejectsUnknownAndInvalidConversationIds() throws Exception {
      assertEquals( 404, send( "GET", "/ame/api/ai/conversations/" + UUID.randomUUID(), null, null ).statusCode() );
      assertEquals( 404, send( "GET", "/ame/api/ai/conversations/not-a-uuid", null, null ).statusCode() );
   }

   @Test
   void deletesAllConversations() throws Exception {
      assertEquals( 204, send( "DELETE", "/ame/api/ai/conversations", null, null ).statusCode() );
      assertEquals( "[]", send( "GET", "/ame/api/ai/conversations", null, null ).body().replaceAll( "\\s", "" ) );
   }

   @Test
   void answersAChatWithoutKeyWithInvalidKey() throws Exception {
      final HttpResponse<String> response = send( "POST", "/ame/api/ai/chat", chatRequest( UUID.randomUUID().toString() ), null );
      assertEquals( 400, response.statusCode() );
      assertEquals( "invalid_key", JSON.readTree( response.body() ).get( "code" ).asText() );
   }

   @Test
   void reportsFailedProviderTestsWithOkFalse() throws Exception {
      final HttpResponse<String> response = send( "POST", "/ame/api/ai/test", "{\"provider\":{\"kind\":\"openai\"}}", null );
      assertEquals( 200, response.statusCode() );
      final JsonNode result = JSON.readTree( response.body() );
      assertFalse( result.get( "ok" ).asBoolean() );
      assertEquals( "invalid_key", result.get( "code" ).asText() );

      final HttpResponse<String> ok = send( "POST", "/ame/api/ai/test", "{\"provider\":{\"kind\":\"openai\"}}", KEY );
      assertTrue( JSON.readTree( ok.body() ).get( "ok" ).asBoolean(), ok.body() );
   }

   @Test
   void listsModels() throws Exception {
      final HttpResponse<String> response = send( "POST", "/ame/api/ai/models", "{\"provider\":{\"kind\":\"ollama\"}}", null );
      assertEquals( 200, response.statusCode() );
      assertEquals( "scripted-model", JSON.readTree( response.body() ).get( 0 ).get( "id" ).asText() );
   }

   @Test
   void validatesProposals() throws Exception {
      final HttpResponse<String> response = send( "POST", "/ame/api/ai/proposals/validate",
            JSON.writeValueAsString( Map.of( "turtle", "this is not turtle" ) ), null );
      assertEquals( 200, response.statusCode() );
      final JsonNode result = JSON.readTree( response.body() );
      assertFalse( result.get( "valid" ).asBoolean() );
      assertFalse( result.get( "validationErrors" ).isEmpty() );
   }

   @Test
   void cancelsUnknownRequestsQuietly() throws Exception {
      assertEquals( 204, send( "POST", "/ame/api/ai/chat/" + UUID.randomUUID() + "/cancel", "", null ).statusCode() );
   }

   @Test
   void reportsTheCopilotStatus() throws Exception {
      final HttpResponse<String> response = send( "GET", "/ame/api/ai/copilot/status", null, null );
      assertEquals( 200, response.statusCode() );
      assertFalse( JSON.readTree( response.body() ).get( "available" ).asBoolean() );
      assertEquals( 400, send( "POST", "/ame/api/ai/copilot/login", "", null ).statusCode() );
   }

   @Test
   void attachesFilesAndSendsThemWithTheChat() throws Exception {
      final HttpResponse<String> upload = upload( "notes.md", "# Fleet\nThe vehicles report their speed every second." );
      assertEquals( 200, upload.statusCode(), upload.body() );
      final JsonNode attachment = JSON.readTree( upload.body() );
      assertEquals( "notes.md", attachment.get( "name" ).asText() );
      assertEquals( "text", attachment.get( "kind" ).asText() );
      assertFalse( attachment.get( "truncated" ).asBoolean() );
      final String id = attachment.get( "id" ).asText();

      final String request = JSON.writeValueAsString( Map.of( "requestId", UUID.randomUUID().toString(),
            "provider", Map.of( "kind", "openai", "model", "gpt-test" ), "message", "Summarize the notes",
            "context", Map.of( "attachmentIds", List.of( id ) ) ) );
      final HttpResponse<String> chat = send( "POST", "/ame/api/ai/chat", request, KEY );
      assertEquals( 200, chat.statusCode() );
      assertTrue( ScriptedProvider.lastUserMessage.contains( "every second" ), ScriptedProvider.lastUserMessage );
      final String conversationId = JSON.readTree( parseEvents( chat.body() ).getFirst()[1] ).get( "id" ).asText();
      final JsonNode context = JSON.readTree( send( "GET", "/ame/api/ai/conversations/" + conversationId, null, null ).body() )
            .get( "messages" ).get( 0 ).get( "context" ).get( 0 );
      assertEquals( "file", context.get( "kind" ).asText() );
      assertEquals( "notes.md", context.get( "label" ).asText() );

      assertEquals( 204, send( "DELETE", "/ame/api/ai/attachments/" + id, null, null ).statusCode() );
      assertEquals( 204, send( "DELETE", "/ame/api/ai/attachments/" + id, null, null ).statusCode() );
   }

   @Test
   void rejectsUnsupportedAndEmptyAttachments() throws Exception {
      final HttpResponse<String> unsupported = upload( "tool.exe", "MZ" );
      assertEquals( 400, unsupported.statusCode() );
      assertEquals( "attachment_unsupported", JSON.readTree( unsupported.body() ).get( "code" ).asText() );
      final HttpResponse<String> empty = upload( "empty.txt", "  \n" );
      assertEquals( 400, empty.statusCode() );
      assertEquals( "attachment_empty", JSON.readTree( empty.body() ).get( "code" ).asText() );
   }

   @Test
   void rejectsTooLargeAttachments() throws Exception {
      final HttpResponse<String> tooLarge = upload( "big.txt", "a".repeat( (int) AttachmentTextExtractor.MAX_FILE_SIZE + 1 ) );
      assertEquals( 413, tooLarge.statusCode() );
      final JsonNode error = JSON.readTree( tooLarge.body() );
      assertEquals( "attachment_too_large", error.get( "code" ).asText() );
      assertEquals( AttachmentTextExtractor.TOO_LARGE_MESSAGE, error.get( "message" ).asText() );
   }

   private HttpResponse<String> upload( final String fileName, final String content ) throws IOException, InterruptedException {
      final String boundary = "ame-" + UUID.randomUUID();
      final String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
            + "\"\r\nContent-Type: application/octet-stream\r\n\r\n" + content + "\r\n--" + boundary + "--\r\n";
      final HttpRequest request = HttpRequest.newBuilder( URI.create( server.getURL() + "/ame/api/ai/attachments" ) )
            .header( "Content-Type", "multipart/form-data; boundary=" + boundary )
            .POST( HttpRequest.BodyPublishers.ofString( body ) ).build();
      return http.send( request, HttpResponse.BodyHandlers.ofString() );
   }

   private String chatRequest( final String requestId ) throws IOException {
      return JSON.writeValueAsString( Map.of( "requestId", requestId, "provider", Map.of( "kind", "openai", "model", "gpt-test" ),
            "message", "Show me example payloads" ) );
   }

   private HttpResponse<String> send( final String method, final String path, final String body, final String key )
         throws IOException, InterruptedException {
      final HttpRequest.Builder request = HttpRequest.newBuilder( URI.create( server.getURL() + path ) )
            .method( method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString( body ) );
      if ( body != null ) {
         request.header( "Content-Type", "application/json" );
      }
      if ( key != null ) {
         request.header( "X-Ame-Ai-Key", key );
      }
      return http.send( request.build(), HttpResponse.BodyHandlers.ofString() );
   }

   private static List<String[]> parseEvents( final String body ) {
      final List<String[]> events = new ArrayList<>();
      String name = "message";
      final StringBuilder data = new StringBuilder();
      for ( final String line : body.split( "\r?\n", -1 ) ) {
         if ( line.startsWith( "event:" ) ) {
            name = line.substring( "event:".length() ).trim();
         } else if ( line.startsWith( "data:" ) ) {
            data.append( line.substring( "data:".length() ).trim() );
         } else if ( line.isEmpty() && !data.isEmpty() ) {
            events.add( new String[] { name, data.toString() } );
            name = "message";
            data.setLength( 0 );
         }
      }
      assertNotNull( events );
      return events;
   }

   private static boolean contains( final Path file, final String text ) {
      try {
         return Files.readString( file ).contains( text );
      } catch ( final IOException e ) {
         return false;
      }
   }

   /**
    * Answers with a text and proposes example payloads.
    */
   static class ScriptedProvider implements AiProvider {
      static volatile String lastUserMessage = "";

      @Override
      public Set<AiProviderKind> kinds() {
         return EnumSet.allOf( AiProviderKind.class );
      }

      @Override
      public List<AiModelInfo> listModels( final AiProviderSettings settings ) {
         return List.of( new AiModelInfo( "scripted-model", "Scripted model" ) );
      }

      @Override
      public AiChatResult chat( final AiProviderSettings settings, final AiChatTurn turn, final AiChatListener listener ) {
         lastUserMessage = turn.userMessage();
         listener.onDelta( "Here are " );
         listener.onDelta( "the payloads." );
         listener.onToolCall( "call-1", AiTools.PROPOSE_PAYLOAD,
               "{\"title\":\"Payloads\",\"variants\":[{\"name\":\"valid\",\"json\":\"{\\\"speed\\\":1}\"}]}" );
         return new AiChatResult( "Here are the payloads.", 3L, 4L );
      }
   }

   /**
    * A GitHub Copilot that is not installed.
    */
   static class UnavailableCopilot implements CopilotGateway {
      private static AiException notAvailable() {
         return new AiException( AiErrorCode.NOT_AUTHENTICATED, "GitHub Copilot is not available." );
      }

      @Override
      public boolean isAvailable() {
         return false;
      }

      @Override
      public CopilotStatus status() {
         return new CopilotStatus( false, false, null, null );
      }

      @Override
      public List<AiModelInfo> listModels() {
         throw notAvailable();
      }

      @Override
      public AiChatResult chat( final ChatRequest request ) {
         throw notAvailable();
      }

      @Override
      public CopilotDeviceLogin beginDeviceLogin() {
         throw notAvailable();
      }

      @Override
      public CopilotStatus completeDeviceLogin( final String flowId ) {
         throw notAvailable();
      }

      @Override
      public void cancelDeviceLogin( final String flowId ) {
      }

      @Override
      public CopilotStatus loginWithToken( final String token ) {
         throw notAvailable();
      }

      @Override
      public CopilotStatus logout() {
         return status();
      }
   }
}
