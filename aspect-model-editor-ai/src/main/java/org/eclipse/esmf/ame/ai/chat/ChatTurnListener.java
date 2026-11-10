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

import java.util.function.Consumer;

import org.eclipse.esmf.ame.ai.model.chat.AiChatEvent;
import org.eclipse.esmf.ame.ai.provider.AiCancellation;
import org.eclipse.esmf.ame.ai.provider.AiChatListener;
import org.eclipse.esmf.ame.ai.provider.AiChatResult;
import org.eclipse.esmf.ame.ai.tools.AiToolResult;
import org.eclipse.esmf.ame.ai.tools.AiToolSession;
import org.eclipse.esmf.ame.ai.tools.AiTools;

import io.micronaut.core.annotation.Nullable;

/**
 * Listens to one chat turn: emits the streamed text as {@code delta} events, executes the tool calls (emitting
 * {@code tool} events) and collects the streamed answer.
 */
final class ChatTurnListener implements AiChatListener {
   private final AiTools tools;
   private final AiToolSession session;
   private final AiCancellation cancellation;
   private final Consumer<AiChatEvent> emit;
   private final StringBuilder streamed = new StringBuilder();

   ChatTurnListener( final AiTools tools, final AiToolSession session, final AiCancellation cancellation,
         final Consumer<AiChatEvent> emit ) {
      this.tools = tools;
      this.session = session;
      this.cancellation = cancellation;
      this.emit = emit;
   }

   @Override
   public void onDelta( final String text ) {
      if ( text != null && !text.isEmpty() && !cancellation.isCancelled() ) {
         synchronized ( streamed ) {
            streamed.append( text );
         }
         emit.accept( AiChatEvent.delta( text ) );
      }
   }

   @Override
   public String onToolCall( final String callId, final String name, final String argumentsJson ) {
      cancellation.throwIfCancelled();
      emit.accept( AiChatEvent.tool( callId, name, AiChatEvent.ToolData.STARTED, null ) );
      final AiToolResult result = tools.execute( session, name, argumentsJson );
      emit.accept( AiChatEvent.tool( callId, name, result.failed() ? AiChatEvent.ToolData.FAILED : AiChatEvent.ToolData.DONE,
            result.summary() ) );
      return result.output();
   }

   /**
    * The answer of the turn: the streamed text, or the text of the result if nothing was streamed.
    */
   String answer( final @Nullable AiChatResult result ) {
      synchronized ( streamed ) {
         return result != null && result.text() != null && !result.text().isBlank() && streamed.isEmpty()
               ? result.text()
               : streamed.toString();
      }
   }
}
