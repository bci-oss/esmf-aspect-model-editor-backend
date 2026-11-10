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
package org.eclipse.esmf.ame.ai.model.proposal;

import io.micronaut.serde.annotation.Serdeable;

/**
 * A sample payload.
 *
 * @param name the name of the variant, e.g. "valid" or "boundary values"
 * @param json the payload
 */
@Serdeable
public record AiPayloadVariant( String name, String json ) {
}
