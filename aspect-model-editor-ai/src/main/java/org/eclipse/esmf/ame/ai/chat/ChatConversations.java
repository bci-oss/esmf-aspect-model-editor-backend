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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.eclipse.esmf.ame.ai.conversation.ConversationStore;
import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;
import org.eclipse.esmf.ame.ai.model.chat.AiChatRequest;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversation;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversationMessage;
import org.eclipse.esmf.ame.ai.model.conversation.AiMessageContextRef;
import org.eclipse.esmf.ame.ai.model.proposal.AiProposal;
import org.eclipse.esmf.ame.ai.provider.AiChatMessage;
import org.eclipse.esmf.ame.ai.provider.AiProviderSettings;

import io.micronaut.core.annotation.Nullable;

/**
 * Builds the conversation parts of a chat request: new conversations and their titles, the stored user message, the
 * history sent to the AI and the attachments of earlier messages.
 */
final class ChatConversations {
   static final int MAX_HISTORY = 20;
   static final int MAX_TITLE_LENGTH = 60;

   private ChatConversations() {
   }

   /**
    * Creates a new, empty conversation titled after the request.
    */
   static AiConversation create( final AiChatRequest request, final AiProviderSettings settings, final String now ) {
      return new AiConversation( ConversationStore.newId(), title( request ), now, now, settings.kind().id(), settings.model(), List.of() );
   }

   /**
    * Creates the stored message of the user.
    *
    * @param refs the context the user added, shown with the message
    */
   static AiConversationMessage userMessage( final AiChatRequest request, final List<AiMessageContextRef> refs, final String now ) {
      return new AiConversationMessage( UUID.randomUUID().toString(), AiConversationMessage.USER,
            request.message() == null ? "" : request.message().strip(), blankToNull( request.command() ), null, now, null,
            refs.isEmpty() ? null : refs );
   }

   /**
    * The last {@link #MAX_HISTORY} messages of a conversation as sent to the AI, with the titles of the proposals and the
    * names of the attached files.
    */
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

   /**
    * The title of a new conversation: the message (cut at {@link #MAX_TITLE_LENGTH}) or the command.
    */
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
