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
package org.eclipse.esmf.ame.ai.model.chat;

import org.eclipse.esmf.ame.ai.model.AiError;
import org.eclipse.esmf.ame.ai.model.proposal.AiProposal;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

/**
 * A server-sent event of a chat stream.
 *
 * @param name the event name: {@value #CONVERSATION}, {@value #DELTA}, {@value #TOOL}, {@value #PROPOSAL},
 *       {@value #DONE} or {@value #ERROR}
 * @param data the event data
 */
public record AiChatEvent( String name, Object data ) {
   public static final String CONVERSATION = "conversation";
   public static final String DELTA = "delta";
   public static final String TOOL = "tool";
   public static final String PROPOSAL = "proposal";
   public static final String DONE = "done";
   public static final String ERROR = "error";

   /**
    * @return the {@value #CONVERSATION} event
    */
   public static AiChatEvent conversation( final String id, final String title ) {
      return new AiChatEvent( CONVERSATION, new ConversationData( id, title ) );
   }

   /**
    * @return the {@value #DELTA} event
    */
   public static AiChatEvent delta( final String text ) {
      return new AiChatEvent( DELTA, new DeltaData( text ) );
   }

   /**
    * @return the {@value #TOOL} event
    */
   public static AiChatEvent tool( final String callId, final String toolName, final String status, final @Nullable String summary ) {
      return new AiChatEvent( TOOL, new ToolData( callId, toolName, status, summary ) );
   }

   /**
    * @return the {@value #PROPOSAL} event
    */
   public static AiChatEvent proposal( final AiProposal proposal ) {
      return new AiChatEvent( PROPOSAL, proposal );
   }

   /**
    * @return the {@value #DONE} event
    */
   public static AiChatEvent done( final String messageId, final @Nullable Usage usage ) {
      return new AiChatEvent( DONE, new DoneData( messageId, usage ) );
   }

   /**
    * @return the {@value #ERROR} event
    */
   public static AiChatEvent error( final AiError error ) {
      return new AiChatEvent( ERROR, error );
   }

   /**
    * @return whether the event ends the stream
    */
   public boolean isTerminal() {
      return DONE.equals( name ) || ERROR.equals( name );
   }

   /**
    * Data of the {@value #CONVERSATION} event.
    */
   @Serdeable
   public record ConversationData( String id, String title ) {
   }

   /**
    * Data of the {@value #DELTA} event.
    */
   @Serdeable
   public record DeltaData( String text ) {
   }

   /**
    * Data of the {@value #TOOL} event.
    */
   @Serdeable
   @JsonInclude( JsonInclude.Include.NON_NULL )
   public record ToolData( String callId, String name, String status, @Nullable String summary ) {
      public static final String STARTED = "started";
      public static final String DONE = "done";
      public static final String FAILED = "failed";
   }

   /**
    * Data of the {@value #DONE} event.
    */
   @Serdeable
   @JsonInclude( JsonInclude.Include.NON_NULL )
   public record DoneData( String messageId, @Nullable Usage usage ) {
   }

   /**
    * Token usage of an answer.
    */
   @Serdeable
   @JsonInclude( JsonInclude.Include.NON_NULL )
   public record Usage( @Nullable Long inputTokens, @Nullable Long outputTokens ) {
      /**
       * @return the usage, or {@code null} if both counts are unknown
       */
      public static @Nullable Usage of( final @Nullable Long inputTokens, final @Nullable Long outputTokens ) {
         return inputTokens == null && outputTokens == null ? null : new Usage( inputTokens, outputTokens );
      }
   }
}
