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

import java.util.List;

import org.eclipse.esmf.ame.services.models.refactoring.ChangedFile;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Model files of the local workspaces that were changed by another program, e.g. a text editor.
 *
 * @param created the files that were added
 * @param modified the files whose content changed
 * @param removed the files that were deleted
 */
@Serdeable
@Introspected
public record WorkspaceFilesChanged(
      List<ChangedFile> created,
      List<ChangedFile> modified,
      List<ChangedFile> removed
) {
   public WorkspaceFilesChanged {
      created = List.copyOf( created );
      modified = List.copyOf( modified );
      removed = List.copyOf( removed );
   }

   public boolean isEmpty() {
      return created.isEmpty() && modified.isEmpty() && removed.isEmpty();
   }
}
