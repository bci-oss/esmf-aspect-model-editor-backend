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

import io.micronaut.core.annotation.Nullable;

/**
 * The completed answer of a provider.
 *
 * @param text the complete answer text
 * @param inputTokens the used input tokens if known
 * @param outputTokens the used output tokens if known
 */
public record AiChatResult( String text, @Nullable Long inputTokens, @Nullable Long outputTokens ) {
}
