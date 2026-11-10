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

/**
 * Everything a provider needs to answer one user message.
 *
 * @param systemPrompt the system prompt
 * @param history the previous messages, oldest first
 * @param userMessage the new user message including the context
 * @param tools the tools the model may call
 * @param maxToolRounds the maximum number of model round trips with tool calls
 * @param cancellation signals that the turn was cancelled
 */
public record AiChatTurn(
      String systemPrompt,
      List<AiChatMessage> history,
      String userMessage,
      List<AiToolSpec> tools,
      int maxToolRounds,
      AiCancellation cancellation
) {
   public AiChatTurn {
      history = List.copyOf( history );
      tools = List.copyOf( tools );
   }
}
