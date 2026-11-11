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

import java.util.List;

import org.eclipse.esmf.ame.ai.conversation.ConversationStore;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversation;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversationSummary;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversationTitle;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/**
 * The stored conversations of the AI assistant: list, read, rename and delete.
 */
@Controller( "ai" )
@Produces( MediaType.APPLICATION_JSON )
@ExecuteOn( TaskExecutors.BLOCKING )
public class AiConversationController {
   private final ConversationStore conversationStore;

   public AiConversationController( final ConversationStore conversationStore ) {
      this.conversationStore = conversationStore;
   }

   /**
    * @return the stored conversations, newest first
    */
   @Get( "/conversations" )
   public HttpResponse<List<AiConversationSummary>> getConversations() {
      return HttpResponse.ok( conversationStore.list() );
   }

   /**
    * @param id the conversation id
    * @return the conversation with its messages
    */
   @Get( "/conversations/{id}" )
   public HttpResponse<AiConversation> getConversation( @PathVariable final String id ) {
      return HttpResponse.ok( conversationStore.get( id ) );
   }

   /**
    * @param id the conversation id
    * @param request the new title
    * @return the renamed conversation
    */
   @Patch( "/conversations/{id}" )
   public HttpResponse<AiConversationSummary> renameConversation( @PathVariable final String id, @Body final AiConversationTitle request ) {
      return HttpResponse.ok( conversationStore.rename( id, request.title() ) );
   }

   /**
    * Deletes a conversation.
    *
    * @param id the conversation id
    * @return 204
    */
   @Delete( "/conversations/{id}" )
   public HttpResponse<Void> deleteConversation( @PathVariable final String id ) {
      conversationStore.delete( id );
      return HttpResponse.noContent();
   }

   /**
    * Deletes all conversations.
    *
    * @return 204
    */
   @Delete( "/conversations" )
   public HttpResponse<Void> deleteConversations() {
      conversationStore.deleteAll();
      return HttpResponse.noContent();
   }
}
