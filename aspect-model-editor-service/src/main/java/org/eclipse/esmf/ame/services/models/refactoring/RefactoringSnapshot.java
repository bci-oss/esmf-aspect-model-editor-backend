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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Everything needed to revert an applied refactoring; stored as manifest next to the backed up files.
 *
 * @param entry the refactoring as shown to the user
 * @param files the state of each affected file
 */
@Serdeable
@Introspected
public record RefactoringSnapshot(
      RefactoringHistoryEntry entry,
      List<FileState> files
) {
   public RefactoringSnapshot {
      files = files == null ? List.of() : List.copyOf( files );
   }

   /**
    * How a refactoring changed a file.
    */
   public enum Change {
      CREATED, MODIFIED, REMOVED
   }

   /**
    * The state of one affected file.
    *
    * @param path the absolute path of the file
    * @param change how the refactoring changed the file
    * @param backup the name of the copy of the content before the refactoring, {@code null} for created files
    * @param hashAfter the SHA-256 hash of the content after the refactoring, {@code null} for removed files
    */
   @Serdeable
   @Introspected
   public record FileState(
         String path,
         Change change,
         @Nullable String backup,
         @Nullable String hashAfter
   ) {
   }
}
