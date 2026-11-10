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
package org.eclipse.esmf.ame.ai.copilot;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;
import org.eclipse.esmf.ame.ai.provider.AiChatListener;
import org.eclipse.esmf.ame.ai.provider.AiChatMessage;
import org.eclipse.esmf.ame.ai.provider.AiChatResult;
import org.eclipse.esmf.ame.ai.provider.AiChatTurn;
import org.eclipse.esmf.ame.ai.provider.AiProvider;
import org.eclipse.esmf.ame.ai.provider.AiProviderKind;
import org.eclipse.esmf.ame.ai.provider.AiProviderSettings;

import jakarta.inject.Singleton;

/**
 * The GitHub Copilot provider. Copilot runs the tool loop itself, so the number of tool calls is limited here: after
 * {@value #TOOL_CALLS_PER_ROUND} calls per allowed tool round, further calls are refused and the model is asked to
 * answer.
 */
@Singleton
public class CopilotProvider implements AiProvider {
   static final int TOOL_CALLS_PER_ROUND = 4;
   static final String TOOL_LIMIT_MESSAGE = "Tool call limit reached. Do not call any more tools; answer the user now.";
   private static final long TIMEOUT_MILLIS = 120_000;

   private final CopilotGateway gateway;

   public CopilotProvider( final CopilotGateway gateway ) {
      this.gateway = gateway;
   }

   @Override
   public Set<AiProviderKind> kinds() {
      return EnumSet.of( AiProviderKind.COPILOT );
   }

   @Override
   public boolean isAvailable() {
      return gateway.isAvailable();
   }

   @Override
   public List<AiModelInfo> listModels( final AiProviderSettings settings ) {
      return gateway.listModels();
   }

   @Override
   public AiChatResult chat( final AiProviderSettings settings, final AiChatTurn turn, final AiChatListener listener ) {
      final int maxToolCalls = turn.maxToolRounds() * TOOL_CALLS_PER_ROUND;
      final AtomicInteger toolCalls = new AtomicInteger();
      return gateway.chat( new CopilotGateway.ChatRequest(
            settings.model(),
            turn.systemPrompt(),
            prompt( turn ),
            turn.tools(),
            ( callId, name, arguments ) -> {
               turn.cancellation().throwIfCancelled();
               if ( toolCalls.incrementAndGet() > maxToolCalls ) {
                  return TOOL_LIMIT_MESSAGE;
               }
               return listener.onToolCall( callId, name, arguments );
            },
            listener::onDelta,
            turn.cancellation(),
            TIMEOUT_MILLIS ) );
   }

   /**
    * Every chat uses a new Copilot session, so the previous messages are sent as part of the prompt.
    */
   static String prompt( final AiChatTurn turn ) {
      if ( turn.history().isEmpty() ) {
         return turn.userMessage();
      }
      final StringBuilder prompt = new StringBuilder( "<conversation_history>\n" );
      for ( final AiChatMessage message : turn.history() ) {
         prompt.append( "<" ).append( message.role() ).append( ">\n" )
               .append( message.content() )
               .append( "\n</" ).append( message.role() ).append( ">\n" );
      }
      return prompt.append( "</conversation_history>\n\n" ).append( turn.userMessage() ).toString();
   }
}
