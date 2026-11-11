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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.conversation.ConversationStore;
import org.eclipse.esmf.ame.ai.model.AiError;
import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;
import org.eclipse.esmf.ame.ai.model.chat.AiChatEvent;
import org.eclipse.esmf.ame.ai.model.chat.AiChatRequest;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversation;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversationMessage;
import org.eclipse.esmf.ame.ai.model.proposal.AiProposal;
import org.eclipse.esmf.ame.ai.provider.AiCancellation;
import org.eclipse.esmf.ame.ai.provider.AiChatMessage;
import org.eclipse.esmf.ame.ai.provider.AiChatResult;
import org.eclipse.esmf.ame.ai.provider.AiChatTurn;
import org.eclipse.esmf.ame.ai.provider.AiErrors;
import org.eclipse.esmf.ame.ai.provider.AiProvider;
import org.eclipse.esmf.ame.ai.provider.AiProviderFactory;
import org.eclipse.esmf.ame.ai.provider.AiProviderSettings;
import org.eclipse.esmf.ame.ai.tools.AiToolSession;
import org.eclipse.esmf.ame.ai.tools.AiTools;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the chat requests of the AI assistant: builds the prompt, lets the provider call the tools, streams the events
 * and persists the conversation. The API key is only passed to the provider; it is never stored or logged.
 */
@Singleton
public class AiChatService {
   public static final int MAX_TOOL_ROUNDS = 8;
   static final int MAX_MESSAGE_LENGTH = 100_000;
   static final int MAX_REQUEST_ID_LENGTH = 128;
   private static final Logger LOG = LoggerFactory.getLogger( AiChatService.class );

   private final AiProviderFactory providerFactory;
   private final AiTools tools;
   private final ConversationStore store;
   private final AiContextResolver contextResolver;
   private final Duration timeout;
   private final Map<String, AiCancellation> running = new ConcurrentHashMap<>();
   private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
         Thread.ofPlatform().daemon().name( "ai-chat-timeout" ).factory() );

   @Inject
   public AiChatService( final AiProviderFactory providerFactory, final AiTools tools, final ConversationStore store,
         final AiContextResolver contextResolver, @Value( "${ame.ai.timeout-seconds:120}" ) final long timeoutSeconds ) {
      this( providerFactory, tools, store, contextResolver, Duration.ofSeconds( timeoutSeconds ) );
   }

   public AiChatService( final AiProviderFactory providerFactory, final AiTools tools, final ConversationStore store,
         final AiContextResolver contextResolver, final Duration timeout ) {
      this.providerFactory = providerFactory;
      this.tools = tools;
      this.store = store;
      this.contextResolver = contextResolver;
      this.timeout = timeout;
   }

   /**
    * Starts a chat request. The request is validated immediately; the chat runs when the returned publisher is
    * subscribed and is cancelled (best effort) when the subscriber cancels.
    *
    * @param request the request
    * @param apiKey the API key from the request header, if any
    * @param mapper maps the chat events to the published items
    * @return the events: {@code conversation}, then {@code delta}/{@code tool}/{@code proposal}, then {@code done} or
    *       {@code error}
    * @throws IllegalArgumentException if the request is invalid
    * @throws NoSuchElementException if the conversation does not exist
    * @throws AiException if the provider cannot be used
    */
   public <T> Flow.Publisher<T> chat( final AiChatRequest request, final @Nullable String apiKey, final Function<AiChatEvent, T> mapper ) {
      validate( request );
      final AiProviderSettings settings = AiProviderSettings.of( request.provider(), apiKey );
      final AiProvider provider = providerFactory.get( settings );
      final AiConversation existing = request.conversationId() == null || request.conversationId().isBlank()
            ? null
            : store.get( request.conversationId() );
      final AiCancellation cancellation = new AiCancellation();
      return new AiEventStream<>( mapper,
            stream -> Thread.ofVirtual().name( "ai-chat" ).start( () -> run( request, settings, provider, existing, cancellation,
                  stream::emit ) ),
            cancellation::cancel );
   }

   private static void validate( final @Nullable AiChatRequest request ) {
      if ( request == null ) {
         throw new IllegalArgumentException( "The chat request is missing." );
      }
      if ( request.requestId() == null || request.requestId().isBlank() || request.requestId().length() > MAX_REQUEST_ID_LENGTH ) {
         throw new IllegalArgumentException( "The requestId is missing or too long." );
      }
      final boolean noMessage = request.message() == null || request.message().isBlank();
      if ( noMessage && ( request.command() == null || request.command().isBlank() ) ) {
         throw new IllegalArgumentException( "The message is missing." );
      }
      if ( request.message() != null && request.message().length() > MAX_MESSAGE_LENGTH ) {
         throw new IllegalArgumentException( "The message is too long." );
      }
   }

   /**
    * Cancels a running chat request. Cancellation is best effort: the provider is asked to stop, the answer received so far
    * is kept.
    *
    * @return whether the request was running
    */
   public boolean cancel( final String requestId ) {
      final AiCancellation cancellation = requestId == null ? null : running.get( requestId );
      if ( cancellation == null ) {
         return false;
      }
      cancellation.cancel();
      return true;
   }

   /**
    * Cancels all running chats and stops the timeout timer.
    */
   @PreDestroy
   public void close() {
      running.values().forEach( AiCancellation::cancel );
      timer.shutdownNow();
   }

   void run( final AiChatRequest request, final AiProviderSettings settings, final AiProvider provider,
         final @Nullable AiConversation existing, final AiCancellation cancellation, final Consumer<AiChatEvent> emit ) {
      running.put( request.requestId(), cancellation );
      final AtomicBoolean timedOut = new AtomicBoolean();
      final ScheduledFuture<?> timeoutTask = scheduleTimeout( cancellation, timedOut );
      try {
         final String now = Instant.now().toString();
         final AiConversation started = existing != null ? existing : ChatConversations.create( request, settings, now );
         final List<AiChatMessage> history = ChatConversations.history( started );
         final AiChatContext requestContext = request.contextOrEmpty();
         final AiContextResolver.Resolved resolved = contextResolver.resolve( requestContext );
         final AiConversation conversation = store.save( started.withMessages(
               List.of( ChatConversations.userMessage( request, resolved.refs(), now ) ), settings.kind().id(), settings.model(), now ) );
         emit.accept( AiChatEvent.conversation( conversation.id(), conversation.title() ) );

         final AiChatContext context = ChatConversations.withEarlierAttachments( requestContext, conversation );
         final AiToolSession session = new AiToolSession( context, proposal -> emit.accept( AiChatEvent.proposal( proposal ) ) );
         final ChatTurnListener listener = new ChatTurnListener( tools, session, cancellation, emit );
         final AiChatTurn turn = new AiChatTurn( AiPrompts.system( context ), history, AiPrompts.user( request, resolved ),
               tools.specs( context ), MAX_TOOL_ROUNDS, cancellation );

         final Outcome outcome = callProvider( provider, settings, turn, listener, timedOut );
         final AiConversationMessage answer = saveAnswer( conversation, settings, listener.answer( outcome.result() ),
               session.proposals(), outcome.error() );
         emit.accept( outcome.error() != null
               ? AiChatEvent.error( outcome.error() )
               : AiChatEvent.done( answer.id(), outcome.usage() ) );
      } catch ( final RuntimeException e ) {
         LOG.warn( "AI chat failed", e );
         emit.accept( AiChatEvent.error( new AiError( AiErrorCode.PROVIDER_ERROR.code(),
               settings.scrub( e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage() ) ) ) );
      } finally {
         timeoutTask.cancel( false );
         running.remove( request.requestId(), cancellation );
      }
   }

   /**
    * The outcome of the provider call: the result if it succeeded, the error if it failed, neither if the user cancelled.
    */
   private record Outcome( @Nullable AiChatResult result, @Nullable AiError error ) {
      @Nullable AiChatEvent.Usage usage() {
         return result == null ? null : AiChatEvent.Usage.of( result.inputTokens(), result.outputTokens() );
      }
   }

   /**
    * Cancels the request when the timeout elapses and records that it timed out.
    */
   private ScheduledFuture<?> scheduleTimeout( final AiCancellation cancellation, final AtomicBoolean timedOut ) {
      return timer.schedule( () -> {
         timedOut.set( true );
         cancellation.cancel();
      }, timeout.toMillis(), TimeUnit.MILLISECONDS );
   }

   private Outcome callProvider( final AiProvider provider, final AiProviderSettings settings, final AiChatTurn turn,
         final ChatTurnListener listener, final AtomicBoolean timedOut ) {
      try {
         return new Outcome( provider.chat( settings, turn, listener ), null );
      } catch ( final Exception exception ) {
         final AiError error = toError( exception, settings, timedOut.get() );
         if ( error != null ) {
            LOG.warn( "AI chat with {} failed: {} {}", settings.kind().id(), error.code(), error.message() );
         }
         return new Outcome( null, error );
      }
   }

   /**
    * Stores the answer of the AI. A failure to save is logged; the answer is still sent to the client.
    */
   private AiConversationMessage saveAnswer( final AiConversation conversation, final AiProviderSettings settings, final String text,
         final List<AiProposal> proposals, final @Nullable AiError error ) {
      final AiConversationMessage answer = new AiConversationMessage( UUID.randomUUID().toString(), AiConversationMessage.ASSISTANT,
            text, null, proposals.isEmpty() ? null : proposals, Instant.now().toString(), error, null );
      try {
         store.save( conversation.withMessages( List.of( answer ), settings.kind().id(), settings.model(), answer.createdAt() ) );
      } catch ( final RuntimeException e ) {
         LOG.warn( "The AI conversation could not be saved: {}", e.getMessage() );
      }
      return answer;
   }

   /**
    * Maps a failure of the provider to the error sent to the client.
    *
    * @return the error, {@code null} if the user cancelled the request
    */
   private @Nullable AiError toError( final Exception exception, final AiProviderSettings settings, final boolean timedOut ) {
      final RuntimeException mapped = AiErrors.map( exception, settings );
      if ( mapped instanceof CancellationException ) {
         return timedOut
               ? new AiError( AiErrorCode.TIMEOUT.code(), "The AI did not answer within " + timeout.toSeconds() + " seconds." )
               : null;
      }
      if ( mapped instanceof final AiException aiException ) {
         return aiException.toError();
      }
      return new AiError( AiErrorCode.PROVIDER_ERROR.code(), settings.scrub( mapped.getMessage() ) );
   }
}
