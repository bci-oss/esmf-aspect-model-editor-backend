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
package org.eclipse.esmf.ame.api;

import org.eclipse.esmf.ame.ai.chat.AiChatService;
import org.eclipse.esmf.ame.ai.model.chat.AiChatEvent;
import org.eclipse.esmf.ame.ai.model.chat.AiChatRequest;
import org.eclipse.esmf.ame.constants.ApplicationConstants;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;

/**
 * The chat with the AI assistant: the answer is streamed as Server-Sent Events and a running chat can be cancelled. The API key
 * of the provider is sent per request in the header {@code X-Ame-Ai-Key}; it is never stored or logged.
 */
@Controller( "ai" )
@Produces( MediaType.APPLICATION_JSON )
@ExecuteOn( TaskExecutors.BLOCKING )
public class AiChatController {
   private final AiChatService chatService;

   public AiChatController( final AiChatService chatService ) {
      this.chatService = chatService;
   }

   /**
    * Sends a message to the AI and streams the answer as Server-Sent Events: {@code conversation}, then {@code delta},
    * {@code tool} and {@code proposal} events, and finally {@code done} or {@code error}.
    *
    * @param request the chat request
    * @param apiKey the API key, if the provider requires one
    * @return the events, named after {@link AiChatEvent#name()} with {@link AiChatEvent#data()} as JSON data
    */
   @Post( uri = "/chat", produces = MediaType.TEXT_EVENT_STREAM )
   public Publisher<Event<Object>> chat( @Body final AiChatRequest request,
         @Nullable @Header( ApplicationConstants.Headers.AI_KEY ) final String apiKey ) {
      return FlowAdapters.toPublisher( chatService.chat( request, apiKey, AiChatController::toServerSentEvent ) );
   }

   /**
    * Cancels a running chat request. Unknown or finished requests are ignored.
    *
    * @param requestId the id sent with the chat request
    * @return 204 in any case
    */
   @Post( "/chat/{requestId}/cancel" )
   public HttpResponse<Void> cancelChat( @PathVariable final String requestId ) {
      chatService.cancel( requestId );
      return HttpResponse.noContent();
   }

   static Event<Object> toServerSentEvent( final AiChatEvent event ) {
      return Event.of( event.data() ).name( event.name() );
   }
}
