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
 * A model element that uses another model element, directly or through other elements.
 *
 * @param urn the URN of the using element
 * @param namespace the namespace of the file defining the using element
 * @param version the version of the file defining the using element
 * @param fileName the name of the file defining the using element
 * @param path the absolute path of the file defining the using element
 * @param readOnly whether the file belongs to a read-only source
 * @param external whether the using element is defined in another file than the used element
 */
@Serdeable
@Introspected
public record ElementUsage(
      String urn,
      String namespace,
      String version,
      String fileName,
      String path,
      boolean readOnly,
      boolean external
) {
}
