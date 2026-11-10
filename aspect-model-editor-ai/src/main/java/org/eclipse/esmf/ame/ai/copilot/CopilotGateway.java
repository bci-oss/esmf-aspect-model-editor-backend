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

import java.util.List;
import java.util.function.Consumer;

import org.eclipse.esmf.ame.ai.model.copilot.CopilotDeviceLogin;
import org.eclipse.esmf.ame.ai.model.copilot.CopilotStatus;
import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;
import org.eclipse.esmf.ame.ai.provider.AiCancellation;
import org.eclipse.esmf.ame.ai.provider.AiChatResult;
import org.eclipse.esmf.ame.ai.provider.AiToolSpec;

import io.micronaut.core.annotation.Nullable;

/**
 * Access to GitHub Copilot. Hides the Copilot SDK, so the rest of the assistant can be tested without a login.
 */
public interface CopilotGateway {
   /**
    * @return whether the Copilot runtime is bundled with this application
    */
   boolean isAvailable();

   CopilotStatus status();

   List<AiModelInfo> listModels();

   /**
    * Answers a prompt in a new Copilot session that can only use the given tools.
    *
    * @param request the request
    * @return the answer
    */
   AiChatResult chat( ChatRequest request );

   CopilotDeviceLogin beginDeviceLogin();

   /**
    * Waits until the user entered the code of the device login.
    */
   CopilotStatus completeDeviceLogin( String flowId );

   void cancelDeviceLogin( String flowId );

   CopilotStatus loginWithToken( String token );

   CopilotStatus logout();

   /**
    * A prompt for Copilot.
    *
    * @param model the model or {@code null} for the default
    * @param systemPrompt the system prompt, replaces the one of Copilot
    * @param prompt the prompt including the conversation history
    * @param tools the tools Copilot may use; no other tools are available
    * @param toolExecutor executes the tools
    * @param onDelta receives the streamed answer
    * @param cancellation cancels the request
    * @param timeoutMillis the timeout
    */
   record ChatRequest(
         @Nullable String model,
         String systemPrompt,
         String prompt,
         List<AiToolSpec> tools,
         ToolExecutor toolExecutor,
         Consumer<String> onDelta,
         AiCancellation cancellation,
         long timeoutMillis
   ) {
   }

   /**
    * Executes a tool call of Copilot.
    */
   @FunctionalInterface
   interface ToolExecutor {
      String execute( String callId, String name, String argumentsJson );
   }
}
