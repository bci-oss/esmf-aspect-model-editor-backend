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

/**
 * Receives the streamed answer of a provider and executes the tools.
 */
public interface AiChatListener {
   /**
    * A part of the answer text.
    */
   void onDelta( String text );

   /**
    * Executes a tool call requested by the model.
    *
    * @param callId the id of the call
    * @param name the name of the tool
    * @param argumentsJson the arguments as JSON object
    * @return the result for the model; errors are returned as text so the model can react
    */
   String onToolCall( String callId, String name, String argumentsJson );
}
