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
package org.eclipse.esmf.ame.services.models.workspace;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Where a model element is declared, e.g. to open it in an external text editor.
 *
 * @param path the absolute path of the file declaring the element
 * @param line the 1-based line of the declaration
 * @param column the 1-based column of the declaration
 */
@Serdeable
@Introspected
public record ElementLocation(
      String path,
      int line,
      int column
) {
}
