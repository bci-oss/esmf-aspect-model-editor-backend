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
import java.util.Set;

import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;

/**
 * An AI provider: streams chat answers and executes tool calls requested by the model.
 */
public interface AiProvider {
   /**
    * @return the provider kinds this implementation serves
    */
   Set<AiProviderKind> kinds();

   /**
    * @return whether the provider can be used in this installation
    */
   default boolean isAvailable() {
      return true;
   }

   /**
    * Lists the models of the provider.
    *
    * @throws org.eclipse.esmf.ame.ai.AiException if the provider cannot be reached
    */
   List<AiModelInfo> listModels( AiProviderSettings settings );

   /**
    * Answers one user message. Blocks until the answer is complete. Text is streamed to the listener, tool calls are
    * executed through the listener.
    *
    * @throws org.eclipse.esmf.ame.ai.AiException if the provider fails
    * @throws java.util.concurrent.CancellationException if the turn was cancelled
    */
   AiChatResult chat( AiProviderSettings settings, AiChatTurn turn, AiChatListener listener );
}
