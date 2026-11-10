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

import java.io.IOException;
import java.util.List;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.attachment.AiAttachmentStore;
import org.eclipse.esmf.ame.ai.attachment.AttachmentTextExtractor;
import org.eclipse.esmf.ame.ai.chat.AiChatService;
import org.eclipse.esmf.ame.ai.conversation.ConversationStore;
import org.eclipse.esmf.ame.ai.model.attachment.AiAttachment;
import org.eclipse.esmf.ame.ai.model.chat.AiChatEvent;
import org.eclipse.esmf.ame.ai.model.chat.AiChatRequest;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversation;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversationSummary;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversationTitle;
import org.eclipse.esmf.ame.ai.model.provider.AiLocalServer;
import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderInfo;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderRequest;
import org.eclipse.esmf.ame.ai.model.provider.AiTestResult;
import org.eclipse.esmf.ame.ai.model.validation.AiValidationRequest;
import org.eclipse.esmf.ame.ai.model.validation.AiValidationResult;
import org.eclipse.esmf.ame.ai.provider.AiLocalServerProbe;
import org.eclipse.esmf.ame.ai.provider.AiProviderService;
import org.eclipse.esmf.ame.ai.tools.AiModelValidator;
import org.eclipse.esmf.ame.constants.ApplicationConstants;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;

/**
 * The AI assistant: providers, chat (Server-Sent Events), attachments, conversations and proposal validation. The API key of a
 * provider is sent per request in the header {@code X-Ame-Ai-Key}; it is never stored or logged.
 */
@Controller( "ai" )
@Produces( MediaType.APPLICATION_JSON )
@ExecuteOn( TaskExecutors.BLOCKING )
public class AiController {
   private final AiProviderService providerService;
   private final AiLocalServerProbe localServerProbe;
   private final AiChatService chatService;
   private final ConversationStore conversationStore;
   private final AiModelValidator modelValidator;
   private final AiAttachmentStore attachmentStore;

   public AiController( final AiProviderService providerService, final AiLocalServerProbe localServerProbe,
         final AiChatService chatService, final ConversationStore conversationStore, final AiModelValidator modelValidator,
         final AiAttachmentStore attachmentStore ) {
      this.providerService = providerService;
      this.localServerProbe = localServerProbe;
      this.chatService = chatService;
      this.conversationStore = conversationStore;
      this.modelValidator = modelValidator;
      this.attachmentStore = attachmentStore;
   }

   /**
    * @return the supported providers
    */
   @Get( "/providers" )
   public HttpResponse<List<AiProviderInfo>> getProviders() {
      return HttpResponse.ok( providerService.providers() );
   }

   /**
    * @return the local AI servers found on this machine
    */
   @Get( "/local" )
   public HttpResponse<List<AiLocalServer>> getLocalServers() {
      return HttpResponse.ok( localServerProbe.probe() );
   }

   /**
    * @param request the provider configuration
    * @param apiKey the API key, if the provider requires one
    * @return the models of the provider
    */
   @Post( "/models" )
   public HttpResponse<List<AiModelInfo>> getModels( @Body final AiProviderRequest request,
         @Nullable @Header( ApplicationConstants.Headers.AI_KEY ) final String apiKey ) {
      return HttpResponse.ok( providerService.models( request.provider(), apiKey ) );
   }

   /**
    * Tests a provider configuration. Always answers with 200; a failure is reported with {@code ok: false}.
    */
   @Post( "/test" )
   public HttpResponse<AiTestResult> testProvider( @Body final AiProviderRequest request,
         @Nullable @Header( ApplicationConstants.Headers.AI_KEY ) final String apiKey ) {
      return HttpResponse.ok( providerService.test( request.provider(), apiKey ) );
   }

   /**
    * Sends a message to the AI and streams the answer as Server-Sent Events: {@code conversation}, then {@code delta},
    * {@code tool} and {@code proposal} events, and finally {@code done} or {@code error}.
    */
   @Post( uri = "/chat", produces = MediaType.TEXT_EVENT_STREAM )
   public Publisher<Event<Object>> chat( @Body final AiChatRequest request,
         @Nullable @Header( ApplicationConstants.Headers.AI_KEY ) final String apiKey ) {
      return FlowAdapters.toPublisher( chatService.chat( request, apiKey, AiController::toServerSentEvent ) );
   }

   /**
    * Cancels a running chat request.
    *
    * @param requestId the id sent with the chat request
    */
   @Post( "/chat/{requestId}/cancel" )
   public HttpResponse<Void> cancelChat( @PathVariable final String requestId ) {
      chatService.cancel( requestId );
      return HttpResponse.noContent();
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
    */
   @Delete( "/conversations/{id}" )
   public HttpResponse<Void> deleteConversation( @PathVariable final String id ) {
      conversationStore.delete( id );
      return HttpResponse.noContent();
   }

   /**
    * Deletes all conversations.
    */
   @Delete( "/conversations" )
   public HttpResponse<Void> deleteConversations() {
      conversationStore.deleteAll();
      return HttpResponse.noContent();
   }

   /**
    * Validates the Turtle text of a proposal.
    *
    * @param request the Turtle text
    * @return the validation result
    */
   @Post( "/proposals/validate" )
   public HttpResponse<AiValidationResult> validateProposal( @Body final AiValidationRequest request ) {
      return HttpResponse.ok( modelValidator.validate( request.turtle(), null, null, null ) );
   }

   /**
    * Attaches a file to the chat: its text is extracted and kept in memory (not persisted).
    *
    * @param file the PDF, Word, Excel or text file
    * @return the attachment
    * @throws IOException if the upload cannot be read
    */
   @Post( uri = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA )
   public HttpResponse<AiAttachment> addAttachment( @Part( "file" ) final CompletedFileUpload file ) throws IOException {
      if ( file.getSize() > AttachmentTextExtractor.MAX_FILE_SIZE ) {
         throw new AiException( AiErrorCode.ATTACHMENT_TOO_LARGE, "The file is larger than 20 MB." );
      }
      return HttpResponse.ok( attachmentStore.add( file.getFilename(), file.getBytes() ) );
   }

   /**
    * Removes an attached file.
    *
    * @param id the attachment id
    */
   @Delete( "/attachments/{id}" )
   public HttpResponse<Void> deleteAttachment( @PathVariable final String id ) {
      attachmentStore.delete( id );
      return HttpResponse.noContent();
   }

   static Event<Object> toServerSentEvent( final AiChatEvent event ) {
      return Event.of( event.data() ).name( event.name() );
   }
}
