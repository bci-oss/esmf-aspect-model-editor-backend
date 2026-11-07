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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The progress of the running refactoring, so that the editor can show it for large workspaces.
 *
 * @param running whether a refactoring is running
 * @param phase the current phase, or {@code null} if no refactoring is running
 * @param step the number of the current phase, starting at 1
 * @param steps the number of phases of the running refactoring
 * @param files the number of files that are processed in the current phase
 */
@Serdeable
@Introspected
public record RefactoringProgress(
      boolean running,
      @Nullable RefactoringPhase phase,
      int step,
      int steps,
      int files
) {
   /** No refactoring is running. */
   public static final RefactoringProgress IDLE = new RefactoringProgress( false, null, 0, 0, 0 );
}
