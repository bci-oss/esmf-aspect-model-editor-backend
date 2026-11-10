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

import org.eclipse.esmf.ame.ai.model.AiError;
import org.eclipse.esmf.ame.ai.model.proposal.AiProposal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

/**
 * A message of a conversation.
 *
 * @param id the id of the message
 * @param role {@code user} or {@code assistant}
 * @param content the markdown text
 * @param command the command of a user message
 * @param proposals the proposals of an assistant message
 * @param createdAt when the message was created (ISO-8601)
 * @param error the error that ended an assistant message
 * @param context the files and models the user sent with a user message
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiConversationMessage(
      String id,
      String role,
      String content,
      @Nullable String command,
      @Nullable List<AiProposal> proposals,
      String createdAt,
      @Nullable AiError error,
      @Nullable List<AiMessageContextRef> context
) {
   public static final String USER = "user";
   public static final String ASSISTANT = "assistant";
}
