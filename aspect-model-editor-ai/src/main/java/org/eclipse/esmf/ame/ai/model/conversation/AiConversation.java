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
package org.eclipse.esmf.ame.ai.model.conversation;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

/**
 * A persisted conversation with the AI assistant. It never contains secrets.
 *
 * @param id the id (a UUID)
 * @param title the title
 * @param createdAt when the conversation was created (ISO-8601)
 * @param updatedAt when the conversation was changed last (ISO-8601)
 * @param providerKind the provider of the last answer
 * @param model the model of the last answer
 * @param messages the messages, oldest first
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiConversation(
      String id,
      String title,
      String createdAt,
      String updatedAt,
      @Nullable String providerKind,
      @Nullable String model,
      List<AiConversationMessage> messages
) {
   public AiConversation {
      messages = messages == null ? List.of() : List.copyOf( messages );
   }

   /**
    * @return the summary shown in the list of conversations
    */
   public AiConversationSummary summary() {
      return new AiConversationSummary( id, title, updatedAt, providerKind, model );
   }

   /**
    * @param newTitle the title
    * @param now the timestamp of the change
    * @return a copy with another title
    */
   public AiConversation withTitle( final String newTitle, final String now ) {
      return new AiConversation( id, newTitle, createdAt, now, providerKind, model, messages );
   }

   /**
    * @param added the messages to append
    * @param newProviderKind the provider used
    * @param newModel the model used
    * @param now the timestamp of the change
    * @return a copy with the messages appended
    */
   public AiConversation withMessages( final List<AiConversationMessage> added, final @Nullable String newProviderKind,
         final @Nullable String newModel, final String now ) {
      final List<AiConversationMessage> all = new ArrayList<>( messages );
      all.addAll( added );
      return new AiConversation( id, title, createdAt, now, newProviderKind, newModel, all );
   }
}
