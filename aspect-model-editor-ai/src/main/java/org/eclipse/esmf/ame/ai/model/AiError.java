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
package org.eclipse.esmf.ame.ai.model;

import io.micronaut.serde.annotation.Serdeable;

/**
 * An error of the AI assistant.
 *
 * @param code the stable error code, see {@link org.eclipse.esmf.ame.ai.AiErrorCode}
 * @param message a human-readable message (English)
 */
@Serdeable
public record AiError( String code, String message ) {
}
