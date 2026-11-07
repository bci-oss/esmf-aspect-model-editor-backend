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

package org.eclipse.esmf.ame.services.models;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * An Aspect Model that would be created by importing a submodel template of an Asset Administration Shell.
 *
 * @param urn the URN of the Aspect
 * @param namespace the namespace of the Aspect Model
 * @param version the version of the Aspect Model
 * @param fileName the name of the Aspect Model file
 * @param alreadyExists whether a file with this name already exists in the target workspace
 */
@Serdeable
@Introspected
public record AasImportCandidate(
      String urn,
      String namespace,
      String version,
      String fileName,
      boolean alreadyExists
) {}
