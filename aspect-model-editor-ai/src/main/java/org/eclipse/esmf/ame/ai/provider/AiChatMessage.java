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
 * A previous message of a conversation sent to the model.
 *
 * @param role {@code user} or {@code assistant}
 * @param content the text
 */
public record AiChatMessage( String role, String content ) {
   /**
    * @return whether the message was written by the user
    */
   public boolean isUser() {
      return "user".equals( role );
   }
}
