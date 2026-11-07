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
 * The result of a refactoring dry run: nothing has been written yet.
 *
 * @param description a short description of the refactoring
 * @param report the detailed change report of the ESMF SDK
 * @param created the files that would be created
 * @param modified the files that would be modified
 * @param removed the files that would be removed
 * @param blocked the affected files of read-only sources; the refactoring cannot be applied if there are any
 * @param applicable whether the refactoring changes anything and only writable files; part of the record so that it is
 *       serialized for the editor
 */
@Serdeable
@Introspected
public record RefactoringPreview(
      String description,
      String report,
      List<ChangedFile> created,
      List<ChangedFile> modified,
      List<ChangedFile> removed,
      List<ChangedFile> blocked,
      boolean applicable
) {
   public RefactoringPreview( final String description, final String report, final List<ChangedFile> created,
         final List<ChangedFile> modified, final List<ChangedFile> removed, final List<ChangedFile> blocked ) {
      this( description, report, created, modified, removed, blocked,
            blocked.isEmpty() && !( created.isEmpty() && modified.isEmpty() && removed.isEmpty() ) );
   }
}
