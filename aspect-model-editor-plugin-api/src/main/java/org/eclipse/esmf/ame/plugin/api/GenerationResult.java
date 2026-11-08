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

package org.eclipse.esmf.ame.plugin.api;

import java.util.List;
import java.util.Objects;

/**
 * The result of a generator.
 *
 * @param files the generated files
 * @param warnings problems the user should know about, e.g. parts of the model that were skipped
 */
public record GenerationResult( List<GeneratedFile> files, List<String> warnings ) {
   public GenerationResult {
      files = List.copyOf( Objects.requireNonNull( files, "files" ) );
      warnings = List.copyOf( Objects.requireNonNull( warnings, "warnings" ) );
   }
}
