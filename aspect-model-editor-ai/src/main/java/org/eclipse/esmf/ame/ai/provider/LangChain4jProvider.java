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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;


import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Base class of the providers implemented with LangChain4j. Runs the tool loop: the model is called again with the tool
 * results until it answers without tool calls or the maximum number of tool rounds is reached.
 */
public abstract class LangChain4jProvider implements AiProvider {
   private static final long POLL_MILLIS = 200;

   /**
    * Creates the streaming chat model for a request.
    *
    * @param settings the settings with a resolved model
    */
   protected abstract StreamingChatModel createStreamingModel( AiProviderSettings settings );

   /**
    * @return the settings with the model to use resolved
    */
   protected AiProviderSettings resolveModel( final AiProviderSettings settings ) {
      return settings;
   }

   @Override
   public AiChatResult chat( final AiProviderSettings requestSettings, final AiChatTurn turn, final AiChatListener listener ) {
      try {
         final AiProviderSettings settings = resolveModel( requestSettings );
         final StreamingChatModel model = createStreamingModel( settings );
         final List<ToolSpecification> tools = turn.tools().stream()
               .map( tool -> ToolSpecification.builder()
                     .name( tool.name() )
                     .description( tool.description() )
                     .parameters( JsonSchemas.toObjectSchema( tool.parameters() ) )
                     .build() )
               .toList();

         final List<ChatMessage> messages = new ArrayList<>();
         messages.add( SystemMessage.from( turn.systemPrompt() ) );
         for ( final AiChatMessage message : turn.history() ) {
            messages.add( message.isUser() ? UserMessage.from( message.content() ) : AiMessage.from( message.content() ) );
         }
         messages.add( UserMessage.from( turn.userMessage() ) );

         final StringBuilder text = new StringBuilder();
         TokenUsage usage = null;
         for ( int round = 0; ; round++ ) {
            turn.cancellation().throwIfCancelled();
            final boolean toolsAllowed = round < turn.maxToolRounds() && !tools.isEmpty();
            final ChatRequest.Builder request = ChatRequest.builder().messages( messages );
            if ( toolsAllowed ) {
               request.toolSpecifications( tools );
            }
            final ChatResponse response = stream( model, request.build(), turn.cancellation(), delta -> {
               text.append( delta );
               listener.onDelta( delta );
            } );
            usage = usage == null ? response.tokenUsage() : usage.add( response.tokenUsage() );
            final AiMessage aiMessage = response.aiMessage();
            if ( !toolsAllowed || aiMessage == null || !aiMessage.hasToolExecutionRequests() ) {
               return new AiChatResult( text.toString(), tokens( usage, true ), tokens( usage, false ) );
            }
            messages.add( aiMessage );
            for ( final ToolExecutionRequest call : aiMessage.toolExecutionRequests() ) {
               turn.cancellation().throwIfCancelled();
               final String result = listener.onToolCall( call.id(), call.name(), call.arguments() );
               messages.add( ToolExecutionResultMessage.from( call, result ) );
            }
         }
      } catch ( final RuntimeException exception ) {
         throw AiErrors.map( exception, requestSettings );
      }
   }

   private static Long tokens( final TokenUsage usage, final boolean input ) {
      if ( usage == null ) {
         return null;
      }
      final Integer count = input ? usage.inputTokenCount() : usage.outputTokenCount();
      return count == null ? null : count.longValue();
   }

   private static ChatResponse stream( final StreamingChatModel model, final ChatRequest request, final AiCancellation cancellation,
         final Consumer<String> onDelta ) {
      final CompletableFuture<ChatResponse> result = new CompletableFuture<>();
      final AtomicReference<StreamingHandle> handle = new AtomicReference<>();
      cancellation.onCancel( () -> {
         final StreamingHandle streamingHandle = handle.get();
         if ( streamingHandle != null ) {
            streamingHandle.cancel();
         }
         result.cancel( false );
      } );
      model.chat( request, new StreamingChatResponseHandler() {
         @Override
         public void onPartialResponse( final PartialResponse partialResponse, final PartialResponseContext context ) {
            handle.set( context.streamingHandle() );
            if ( cancellation.isCancelled() ) {
               context.streamingHandle().cancel();
               return;
            }
            if ( partialResponse.text() != null && !partialResponse.text().isEmpty() ) {
               onDelta.accept( partialResponse.text() );
            }
         }

         @Override
         public void onCompleteResponse( final ChatResponse completeResponse ) {
            result.complete( completeResponse );
         }

         @Override
         public void onError( final Throwable error ) {
            result.completeExceptionally( error );
         }
      } );
      while ( true ) {
         cancellation.throwIfCancelled();
         try {
            return result.get( POLL_MILLIS, TimeUnit.MILLISECONDS );
         } catch ( final TimeoutException ignored ) {
            // poll again to notice a cancellation
         } catch ( final InterruptedException exception ) {
            Thread.currentThread().interrupt();
            cancellation.cancel();
         } catch ( final CancellationException exception ) {
            cancellation.throwIfCancelled();
            throw exception;
         } catch ( final ExecutionException exception ) {
            throw exception.getCause() instanceof final RuntimeException runtimeException
                  ? runtimeException
                  : new IllegalStateException( exception.getCause() );
         }
      }
   }
}
