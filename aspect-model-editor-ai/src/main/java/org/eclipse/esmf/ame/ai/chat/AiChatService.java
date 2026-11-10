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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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
import org.eclipse.esmf.ame.ai.model.conversation.AiMessageContextRef;
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
   static final int MAX_HISTORY = 20;
   static final int MAX_TITLE_LENGTH = 60;
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
    * subscribed and is cancelled when the subscriber cancels.
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
    * Cancels a running chat request.
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
      final ScheduledFuture<?> timeoutTask = timer.schedule( () -> {
         timedOut.set( true );
         cancellation.cancel();
      }, timeout.toMillis(), TimeUnit.MILLISECONDS );
      try {
         final String providerKind = settings.kind().id();
         final String now = Instant.now().toString();
         AiConversation conversation = existing != null
               ? existing
               : new AiConversation( ConversationStore.newId(), title( request ), now, now, providerKind, settings.model(), List.of() );
         final List<AiChatMessage> history = history( conversation );
         final AiChatContext requestContext = request.contextOrEmpty();
         final AiContextResolver.Resolved resolved = contextResolver.resolve( requestContext );
         final AiConversationMessage userMessage = new AiConversationMessage( UUID.randomUUID().toString(), AiConversationMessage.USER,
               request.message() == null ? "" : request.message().strip(), blankToNull( request.command() ), null, now, null,
               resolved.refs().isEmpty() ? null : resolved.refs() );
         conversation = store.save( conversation.withMessages( List.of( userMessage ), providerKind, settings.model(), now ) );
         emit.accept( AiChatEvent.conversation( conversation.id(), conversation.title() ) );

         final AiChatContext context = withEarlierAttachments( requestContext, conversation );
         final AiToolSession session = new AiToolSession( context, proposal -> emit.accept( AiChatEvent.proposal( proposal ) ) );
         final ChatTurnListener listener = new ChatTurnListener( tools, session, cancellation, emit );
         final AiChatTurn turn = new AiChatTurn( AiPrompts.system( context ), history, AiPrompts.user( request, resolved ), tools.specs( context ),
               MAX_TOOL_ROUNDS, cancellation );

         AiChatResult result = null;
         AiError error = null;
         try {
            result = provider.chat( settings, turn, listener );
         } catch ( final Throwable throwable ) {
            error = toError( throwable, settings, timedOut.get() );
            if ( error != null ) {
               LOG.warn( "AI chat with {} failed: {} {}", providerKind, error.code(), error.message() );
            }
         }

         final String text = listener.answer( result );
         final List<AiProposal> proposals = session.proposals();
         final AiConversationMessage answer = new AiConversationMessage( UUID.randomUUID().toString(), AiConversationMessage.ASSISTANT,
               text, null, proposals.isEmpty() ? null : proposals, Instant.now().toString(), error, null );
         try {
            store.save( conversation.withMessages( List.of( answer ), providerKind, settings.model(), answer.createdAt() ) );
         } catch ( final RuntimeException e ) {
            LOG.warn( "The AI conversation could not be saved: {}", e.getMessage() );
         }
         if ( error != null ) {
            emit.accept( AiChatEvent.error( error ) );
         } else {
            emit.accept( AiChatEvent.done( answer.id(),
                  result == null ? null : AiChatEvent.Usage.of( result.inputTokens(), result.outputTokens() ) ) );
         }
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
    * Maps a failure of the provider to the error sent to the client.
    *
    * @return the error, {@code null} if the user cancelled the request
    */
   private @Nullable AiError toError( final Throwable throwable, final AiProviderSettings settings, final boolean timedOut ) {
      final RuntimeException mapped = AiErrors.map( throwable, settings );
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

   static List<AiChatMessage> history( final AiConversation conversation ) {
      final List<AiChatMessage> history = new ArrayList<>();
      for ( final AiConversationMessage message : conversation.messages() ) {
         String content = message.content() == null ? "" : message.content();
         if ( AiConversationMessage.ASSISTANT.equals( message.role() ) && message.proposals() != null && !message.proposals().isEmpty() ) {
            content = content + "\n\n(Proposals shown to the user: " + String.join( ", ",
                  message.proposals().stream().map( AiProposal::title ).toList() ) + ")";
         }
         final List<String> attached = message.context() == null ? List.of() : message.context().stream()
               .filter( ref -> AiMessageContextRef.FILE.equals( ref.kind() ) )
               .map( ref -> ref.label() + ( ref.id() == null ? "" : " [attachment id " + ref.id() + "]" ) ).toList();
         if ( !attached.isEmpty() ) {
            content = content + "\n\n(Attached: " + String.join( ", ", attached ) + ")";
         }
         if ( !content.isBlank() ) {
            history.add( new AiChatMessage( message.role(), content.strip() ) );
         }
      }
      return history.size() > MAX_HISTORY ? List.copyOf( history.subList( history.size() - MAX_HISTORY, history.size() ) ) : history;
   }

   /**
    * Adds the attachments of earlier messages, so that the AI can still read them with {@code readAttachment}.
    */
   static AiChatContext withEarlierAttachments( final AiChatContext context, final AiConversation conversation ) {
      final Set<String> ids = new LinkedHashSet<>( context.attachments() );
      conversation.messages().stream().filter( message -> message.context() != null )
            .flatMap( message -> message.context().stream() )
            .filter( ref -> AiMessageContextRef.FILE.equals( ref.kind() ) && ref.id() != null )
            .forEach( ref -> ids.add( ref.id() ) );
      if ( ids.size() == context.attachments().size() ) {
         return context;
      }
      return new AiChatContext( context.modelTurtle(), context.modelUrn(), context.namespace(), context.version(), context.fileName(),
            context.workspaceId(), context.readOnly(), context.selectedElementUrn(), context.mentionedElementUrns(),
            context.validationErrors(), context.languages(), List.copyOf( ids ), context.contextModels() );
   }

   static String title( final AiChatRequest request ) {
      final String message = request.message() == null ? "" : request.message().strip().replaceAll( "\\s+", " " );
      if ( message.isEmpty() ) {
         final String command = request.command() == null ? "" : request.command().strip();
         return command.isEmpty() ? "New conversation" : command.substring( 0, 1 ).toUpperCase( Locale.ROOT ) + command.substring( 1 );
      }
      if ( message.length() <= MAX_TITLE_LENGTH ) {
         return message;
      }
      final int space = message.lastIndexOf( ' ', MAX_TITLE_LENGTH - 1 );
      return message.substring( 0, space > MAX_TITLE_LENGTH / 2 ? space : MAX_TITLE_LENGTH - 1 ) + "…";
   }

   private static @Nullable String blankToNull( final @Nullable String value ) {
      return value == null || value.isBlank() ? null : value.strip();
   }
}
