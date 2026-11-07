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

import java.util.List;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * An applied refactoring that can be reverted.
 *
 * @param id the id used to revert the refactoring
 * @param description a short description of the refactoring
 * @param timestamp the time the refactoring was applied, in milliseconds since the epoch
 * @param created the files that were created
 * @param modified the files that were modified
 * @param removed the files that were removed
 */
@Serdeable
@Introspected
public record RefactoringHistoryEntry(
      String id,
      String description,
      long timestamp,
      List<ChangedFile> created,
      List<ChangedFile> modified,
      List<ChangedFile> removed
) {
   public RefactoringHistoryEntry {
      created = created == null ? List.of() : List.copyOf( created );
      modified = modified == null ? List.of() : List.copyOf( modified );
      removed = removed == null ? List.of() : List.copyOf( removed );
   }
}
