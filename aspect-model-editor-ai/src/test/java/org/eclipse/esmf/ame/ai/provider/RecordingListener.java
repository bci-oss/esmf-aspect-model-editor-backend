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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records the deltas and tool calls of a provider and answers tool calls with a fixed result.
 */
final class RecordingListener implements AiChatListener {
   record ToolCall( String callId, String name, String argumentsJson ) {
   }

   final List<String> deltas = new CopyOnWriteArrayList<>();
   final List<ToolCall> toolCalls = new CopyOnWriteArrayList<>();

   @Override
   public void onDelta( final String text ) {
      deltas.add( text );
   }

   @Override
   public String onToolCall( final String callId, final String name, final String argumentsJson ) {
      toolCalls.add( new ToolCall( callId, name, argumentsJson ) );
      return "kilometrePerHour (km/h)";
   }

   String text() {
      return String.join( "", deltas );
   }

   static AiChatTurn turn( final int maxToolRounds ) {
      return new AiChatTurn( "You are a test.", List.of( new AiChatMessage( "user", "Earlier question" ),
            new AiChatMessage( "assistant", "Earlier answer" ) ), "Which unit for speed?",
            List.of( new AiToolSpec( "listUnits", "Searches units",
                  Map.of( "type", "object", "properties", Map.of( "query", Map.of( "type", "string" ) ), "required", List.of( "query" ) ) ) ),
            maxToolRounds, new AiCancellation() );
   }
}
