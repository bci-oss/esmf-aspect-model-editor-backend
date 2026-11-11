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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.TestDirectories;
import org.eclipse.esmf.ame.ai.attachment.AiAttachmentStore;
import org.eclipse.esmf.ame.ai.conversation.ConversationStore;
import org.eclipse.esmf.ame.ai.model.AiError;
import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;
import org.eclipse.esmf.ame.ai.model.chat.AiChatEvent;
import org.eclipse.esmf.ame.ai.model.chat.AiChatRequest;
import org.eclipse.esmf.ame.ai.model.attachment.AiAttachment;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversation;
import org.eclipse.esmf.ame.ai.model.conversation.AiMessageContextRef;
import org.eclipse.esmf.ame.ai.model.proposal.AiProposal;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderConfig;
import org.eclipse.esmf.ame.ai.model.validation.AiValidationError;
import org.eclipse.esmf.ame.ai.model.validation.AiValidationResult;
import org.eclipse.esmf.ame.ai.provider.AiCancellation;
import org.eclipse.esmf.ame.ai.provider.AiChatResult;
import org.eclipse.esmf.ame.ai.provider.AiProviderFactory;
import org.eclipse.esmf.ame.ai.provider.AiProviderSettings;
import org.eclipse.esmf.ame.ai.tools.AiModelValidator;
import org.eclipse.esmf.ame.ai.tools.AiTools;
import org.eclipse.esmf.ame.services.ModelService;
import org.eclipse.esmf.ame.services.workspace.ElementUsageService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;

import dev.langchain4j.exception.AuthenticationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiChatServiceTest {
   private static final String API_KEY = "sk-secret-test-key-1234";
   private static final String TURTLE = "@prefix : <urn:samm:org.example:1.0.0#> .";
   private static final AiValidationResult INVALID = new AiValidationResult( false,
         List.of( new AiValidationError( "Property without characteristic", "urn:samm:org.example:1.0.0#speed" ) ) );

   private Path directory;
   private ConversationStore store;
   private FakeAiProvider provider;
   private AiModelValidator validator;
   private AiChatService service;
   private WorkspaceReferenceService workspaceReferenceService;
   private AiAttachmentStore attachmentStore;

   @BeforeEach
   void setUp() {
      directory = TestDirectories.create( "chat" );
      store = new ConversationStore( directory );
      provider = new FakeAiProvider();
      validator = mock( AiModelValidator.class );
      workspaceReferenceService = mock( WorkspaceReferenceService.class );
      when( workspaceReferenceService.listWorkspaceFiles() ).thenReturn( List.of() );
      attachmentStore = new AiAttachmentStore();
      final AiTools tools = new AiTools( validator, mock( ModelService.class ), mock( ElementUsageService.class ),
            workspaceReferenceService, attachmentStore );
      service = new AiChatService( new AiProviderFactory( List.of( provider ), false ), tools, store, resolver(),
            Duration.ofSeconds( 10 ) );
   }

   private AiContextResolver resolver() {
      return new AiContextResolver( attachmentStore, workspaceReferenceService );
   }

   @AfterEach
   void tearDown() {
      service.close();
      TestDirectories.delete( directory );
   }

   private static AiChatRequest request( final String requestId, final String conversationId, final AiChatContext context ) {
      return new AiChatRequest( conversationId, requestId, new AiProviderConfig( "openai", "gpt-test", null, null ), "Add a property",
            null, context );
   }

   private static AiChatContext model() {
      return new AiChatContext( TURTLE, "urn:samm:org.example:1.0.0#Movement", "org.example", "1.0.0", "Movement.ttl", null, false,
            null, null, null, List.of( "en" ), null, null );
   }

   private List<AiChatEvent> run( final AiChatRequest request ) throws InterruptedException {
      final Collector collector = new Collector();
      service.chat( request, API_KEY, Function.identity() ).subscribe( collector );
      assertTrue( collector.completed.await( 10, TimeUnit.SECONDS ), "The stream did not complete" );
      return collector.events;
   }

   private static List<String> names( final List<AiChatEvent> events ) {
      return events.stream().map( AiChatEvent::name ).toList();
   }

   @Test
   void streamsConversationDeltaAndDoneAndPersistsTheConversationWithoutKey() throws Exception {
      final List<AiChatEvent> events = run( request( "r1", null, model() ) );

      assertEquals( List.of( AiChatEvent.CONVERSATION, AiChatEvent.DELTA, AiChatEvent.DONE ), names( events ) );
      final AiChatEvent.ConversationData conversation = (AiChatEvent.ConversationData) events.getFirst().data();
      assertTrue( ConversationStore.isValidId( conversation.id() ) );
      assertEquals( "Add a property", conversation.title() );
      final AiChatEvent.DoneData done = (AiChatEvent.DoneData) events.getLast().data();
      assertEquals( 1L, done.usage().inputTokens() );

      final AiConversation stored = store.get( conversation.id() );
      assertEquals( 2, stored.messages().size() );
      assertEquals( "Hello", stored.messages().get( 1 ).content() );
      assertEquals( done.messageId(), stored.messages().get( 1 ).id() );
      assertEquals( "openai", stored.providerKind() );
      assertEquals( API_KEY, provider.settings().getFirst().apiKey() );
      try ( final Stream<Path> files = Files.list( directory ) ) {
         for ( final Path file : files.toList() ) {
            assertFalse( Files.readString( file ).contains( API_KEY ), "The API key was persisted" );
         }
      }
      assertEquals( AiChatService.MAX_TOOL_ROUNDS, provider.turns().getFirst().maxToolRounds() );
      assertEquals( 8, provider.turns().getFirst().maxToolRounds() );
   }

   @Test
   void continuesAnExistingConversationWithHistory() throws Exception {
      final List<AiChatEvent> first = run( request( "r1", null, model() ) );
      final String id = ( (AiChatEvent.ConversationData) first.getFirst().data() ).id();

      final List<AiChatEvent> second = run( request( "r2", id, model() ) );

      assertEquals( id, ( (AiChatEvent.ConversationData) second.getFirst().data() ).id() );
      assertEquals( 2, provider.turns().get( 1 ).history().size() );
      assertEquals( 4, store.get( id ).messages().size() );
   }

   @Test
   void sendsAttachmentsAndKeepsThemAvailableInLaterTurns() throws Exception {
      final AiAttachment attachment = attachmentStore.add( "spec.md", "Speed is measured in km/h".getBytes( StandardCharsets.UTF_8 ) );
      final AiChatContext withAttachment = new AiChatContext( null, null, null, null, null, null, false, null, null, null, null,
            List.of( attachment.id() ), null );

      final List<AiChatEvent> first = run( request( "r1", null, withAttachment ) );
      final String id = ( (AiChatEvent.ConversationData) first.getFirst().data() ).id();

      assertTrue( provider.turns().getFirst().userMessage().contains( "Speed is measured in km/h" ) );
      assertTrue( provider.turns().getFirst().tools().stream().anyMatch( tool -> AiTools.READ_ATTACHMENT.equals( tool.name() ) ) );
      final List<AiMessageContextRef> refs = store.get( id ).messages().getFirst().context();
      assertEquals( 1, refs.size() );
      assertEquals( AiMessageContextRef.FILE, refs.getFirst().kind() );
      assertEquals( "spec.md", refs.getFirst().label() );
      assertEquals( attachment.id(), refs.getFirst().id() );

      run( request( "r2", id, model() ) );

      final var second = provider.turns().get( 1 );
      assertTrue( second.history().getFirst().content().contains( "(Attached: spec.md" ) );
      assertTrue( second.history().getFirst().content().contains( attachment.id() ) );
      assertTrue( second.tools().stream().anyMatch( tool -> AiTools.READ_ATTACHMENT.equals( tool.name() ) ) );
      assertFalse( second.userMessage().contains( "Speed is measured in km/h" ) );
   }

   @Test
   void rejectsUnknownOrInvalidConversationIds() {
      assertThrows( NoSuchElementException.class,
            () -> service.chat( request( "r1", UUID.randomUUID().toString(), model() ), API_KEY, Function.identity() ) );
      assertThrows( NoSuchElementException.class,
            () -> service.chat( request( "r1", "../../etc/passwd", model() ), API_KEY, Function.identity() ) );
   }

   @Test
   void rejectsMissingKeyAndInvalidRequests() {
      final AiException exception = assertThrows( AiException.class,
            () -> service.chat( request( "r1", null, model() ), null, Function.identity() ) );
      assertEquals( AiErrorCode.INVALID_KEY, exception.getCode() );
      assertThrows( IllegalArgumentException.class,
            () -> service.chat( new AiChatRequest( null, "", new AiProviderConfig( "ollama", null, null, null ), "hi", null, null ), null,
                  Function.identity() ) );
      assertThrows( IllegalArgumentException.class,
            () -> service.chat( new AiChatRequest( null, "r1", new AiProviderConfig( "unknown", null, null, null ), "hi", null, null ),
                  null, Function.identity() ) );
   }

   @Test
   void returnsInvalidModelsToTheAiAtMostTwiceThenProposesThemWithErrors() throws Exception {
      when( validator.validate( anyString(), any(), any(), any() ) ).thenReturn( INVALID );
      final List<String> toolOutputs = new CopyOnWriteArrayList<>();
      provider.setScript( ( settings, turn, listener ) -> {
         for ( int attempt = 1; attempt <= 3; attempt++ ) {
            toolOutputs.add( listener.onToolCall( "call-" + attempt, AiTools.PROPOSE_MODEL_CHANGE,
                  "{\"turtle\":\"" + TURTLE.replace( "\"", "\\\"" ) + "\",\"title\":\"Add speed\"}" ) );
         }
         listener.onDelta( "Done." );
         return new AiChatResult( "Done.", null, null );
      } );

      final List<AiChatEvent> events = run( request( "r1", null, model() ) );

      assertTrue( toolOutputs.get( 0 ).contains( "NOT proposed" ) );
      assertTrue( toolOutputs.get( 1 ).contains( "NOT proposed" ) );
      assertTrue( toolOutputs.get( 2 ).contains( "still invalid" ) );
      final List<AiChatEvent.ToolData> toolEvents = events.stream().filter( event -> AiChatEvent.TOOL.equals( event.name() ) )
            .map( event -> (AiChatEvent.ToolData) event.data() ).toList();
      assertEquals( List.of( "started", "failed", "started", "failed", "started", "done" ),
            toolEvents.stream().map( AiChatEvent.ToolData::status ).toList() );
      final List<AiProposal> proposals = events.stream().filter( event -> AiChatEvent.PROPOSAL.equals( event.name() ) )
            .map( event -> (AiProposal) event.data() ).toList();
      assertEquals( 1, proposals.size() );
      assertFalse( proposals.getFirst().valid() );
      assertEquals( TURTLE, proposals.getFirst().originalTurtle() );
      assertEquals( AiChatEvent.DONE, events.getLast().name() );
      final String id = ( (AiChatEvent.ConversationData) events.getFirst().data() ).id();
      assertEquals( 1, store.get( id ).messages().get( 1 ).proposals().size() );
   }

   @Test
   void refusesModelChangesOfReadOnlyModels() throws Exception {
      final List<String> toolOutputs = new CopyOnWriteArrayList<>();
      provider.setScript( ( settings, turn, listener ) -> {
         assertFalse( turn.tools().stream().anyMatch( tool -> AiTools.PROPOSE_MODEL_CHANGE.equals( tool.name() ) ) );
         toolOutputs.add( listener.onToolCall( "c1", AiTools.PROPOSE_MODEL_CHANGE, "{\"turtle\":\"x\",\"title\":\"t\"}" ) );
         return new AiChatResult( "", null, null );
      } );
      final AiChatContext readOnly = new AiChatContext( TURTLE, null, "org.example", "1.0.0", "Movement.ttl", null, true, null, null,
            null, null, null, null );

      final List<AiChatEvent> events = run( request( "r1", null, readOnly ) );

      assertTrue( toolOutputs.getFirst().startsWith( "read_only" ) );
      assertFalse( names( events ).contains( AiChatEvent.PROPOSAL ) );
   }

   @Test
   void mapsProviderErrorsToCodesWithoutLeakingTheKey() throws Exception {
      provider.setScript( ( settings, turn, listener ) -> {
         throw new AuthenticationException( "Incorrect API key provided: " + API_KEY );
      } );
      List<AiChatEvent> events = run( request( "r1", null, model() ) );
      AiError error = (AiError) events.getLast().data();
      assertEquals( AiChatEvent.ERROR, events.getLast().name() );
      assertEquals( "invalid_key", error.code() );
      assertFalse( error.message().contains( API_KEY ) );

      provider.setScript( ( settings, turn, listener ) -> {
         throw new IllegalStateException( "Server said: bad key " + API_KEY );
      } );
      events = run( request( "r2", null, model() ) );
      error = (AiError) events.getLast().data();
      assertEquals( "provider_error", error.code() );
      assertFalse( error.message().contains( API_KEY ) );
      final String id = ( (AiChatEvent.ConversationData) events.getFirst().data() ).id();
      assertEquals( "provider_error", store.get( id ).messages().get( 1 ).error().code() );
   }

   @Test
   void cancelsARunningRequest() throws Exception {
      final CountDownLatch started = new CountDownLatch( 1 );
      provider.setScript( ( settings, turn, listener ) -> {
         listener.onDelta( "Partial" );
         started.countDown();
         while ( !turn.cancellation().isCancelled() ) {
            Thread.sleep( 10 );
         }
         turn.cancellation().throwIfCancelled();
         return null;
      } );
      final Collector collector = new Collector();
      service.chat( request( "cancel-me", null, model() ), API_KEY, Function.identity() ).subscribe( collector );
      assertTrue( started.await( 10, TimeUnit.SECONDS ) );

      assertTrue( service.cancel( "cancel-me" ) );

      assertTrue( collector.completed.await( 10, TimeUnit.SECONDS ) );
      assertEquals( AiChatEvent.DONE, collector.events.getLast().name() );
      assertFalse( service.cancel( "cancel-me" ) );
      final String id = ( (AiChatEvent.ConversationData) collector.events.getFirst().data() ).id();
      assertEquals( "Partial", store.get( id ).messages().get( 1 ).content() );
   }

   @Test
   void cancelsWhenTheClientClosesTheStream() throws Exception {
      final CountDownLatch cancelled = new CountDownLatch( 1 );
      provider.setScript( ( settings, turn, listener ) -> {
         turn.cancellation().onCancel( cancelled::countDown );
         listener.onDelta( "x" );
         while ( !turn.cancellation().isCancelled() ) {
            Thread.sleep( 10 );
         }
         turn.cancellation().throwIfCancelled();
         return null;
      } );
      final Collector collector = new Collector();
      service.chat( request( "r1", null, model() ), API_KEY, Function.identity() ).subscribe( collector );
      collector.firstEvent.await( 10, TimeUnit.SECONDS );

      collector.subscription.cancel();

      assertTrue( cancelled.await( 10, TimeUnit.SECONDS ) );
   }

   @Test
   void reportsATimeout() throws Exception {
      service = new AiChatService( new AiProviderFactory( List.of( provider ), false ),
            new AiTools( validator, mock( ModelService.class ), mock( ElementUsageService.class ), mock( WorkspaceReferenceService.class ),
                  attachmentStore ), store, resolver(), Duration.ofMillis( 200 ) );
      provider.setScript( ( settings, turn, listener ) -> {
         while ( !turn.cancellation().isCancelled() ) {
            Thread.sleep( 10 );
         }
         turn.cancellation().throwIfCancelled();
         return null;
      } );

      final List<AiChatEvent> events = run( request( "r1", null, model() ) );

      assertEquals( AiChatEvent.ERROR, events.getLast().name() );
      assertEquals( "timeout", ( (AiError) events.getLast().data() ).code() );
   }

   @Test
   void letsErrorsOfTheProviderPassButUnregistersTheRequest() {
      provider.setScript( ( settings, turn, listener ) -> {
         throw new LinkageError( "broken provider" );
      } );
      final AiChatRequest request = request( "r-error", null, model() );
      final List<AiChatEvent> events = new CopyOnWriteArrayList<>();

      assertThrows( LinkageError.class, () -> service.run( request, AiProviderSettings.of( request.provider(), API_KEY ), provider, null,
            new AiCancellation(), events::add ) );

      assertEquals( AiChatEvent.CONVERSATION, events.getFirst().name() );
      assertFalse( service.cancel( "r-error" ) );
   }

   @Test
   void enforcesTheLocalOnlyMode() {
      service = new AiChatService( new AiProviderFactory( List.of( provider ), true ),
            new AiTools( validator, mock( ModelService.class ), mock( ElementUsageService.class ), mock( WorkspaceReferenceService.class ),
                  attachmentStore ), store, resolver(), Duration.ofSeconds( 10 ) );
      final AiException exception = assertThrows( AiException.class,
            () -> service.chat( request( "r1", null, model() ), API_KEY, Function.identity() ) );
      assertEquals( AiErrorCode.LOCAL_ONLY, exception.getCode() );
      assertInstanceOf( Flow.Publisher.class, service.chat(
            new AiChatRequest( null, "r2", new AiProviderConfig( "ollama", null, null, null ), "hi", null, null ), null,
            Function.identity() ) );
   }

   @Test
   void createsTitles() {
      assertEquals( "Explain", ChatConversations.title( new AiChatRequest( null, "r", null, "", "explain", null ) ) );
      final String title = ChatConversations.title( new AiChatRequest( null, "r", null, "word ".repeat( 40 ), null, null ) );
      assertTrue( title.length() <= ChatConversations.MAX_TITLE_LENGTH );
      assertTrue( title.endsWith( "…" ) );
   }

   private static final class Collector implements Flow.Subscriber<AiChatEvent> {
      final List<AiChatEvent> events = new CopyOnWriteArrayList<>();
      final CountDownLatch completed = new CountDownLatch( 1 );
      final CountDownLatch firstEvent = new CountDownLatch( 1 );
      volatile Flow.Subscription subscription;

      @Override
      public void onSubscribe( final Flow.Subscription newSubscription ) {
         subscription = newSubscription;
         newSubscription.request( Long.MAX_VALUE );
      }

      @Override
      public void onNext( final AiChatEvent item ) {
         events.add( item );
         firstEvent.countDown();
      }

      @Override
      public void onError( final Throwable throwable ) {
         completed.countDown();
      }

      @Override
      public void onComplete() {
         completed.countDown();
      }
   }
}
