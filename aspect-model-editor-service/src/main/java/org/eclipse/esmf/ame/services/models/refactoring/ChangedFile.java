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

package org.eclipse.esmf.ame.services.models.refactoring;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * An Aspect Model file that a refactoring creates, modifies or removes.
 *
 * @param namespace the namespace of the file
 * @param version the version of the file
 * @param fileName the file name
 * @param path the absolute path of the file
 * @param readOnly whether the file belongs to a read-only source
 */
@Serdeable
@Introspected
public record ChangedFile(
      String namespace,
      String version,
      String fileName,
      String path,
      boolean readOnly
) {
}
