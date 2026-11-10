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

import java.util.Map;

/**
 * A tool the model may call.
 *
 * @param name the name
 * @param description what the tool does
 * @param parameters the JSON schema of the arguments (an object schema)
 */
public record AiToolSpec( String name, String description, Map<String, Object> parameters ) {
}
