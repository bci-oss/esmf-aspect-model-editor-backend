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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

/**
 * An entry of the conversation list.
 *
 * @param id the id
 * @param title the title
 * @param updatedAt when the conversation was changed last (ISO-8601)
 * @param providerKind the provider of the last answer
 * @param model the model of the last answer
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiConversationSummary( String id, String title, String updatedAt, @Nullable String providerKind, @Nullable String model ) {
}
