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
package org.eclipse.esmf.ame.services.refactoring;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.eclipse.esmf.ame.services.models.refactoring.RefactoringPhase;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringProgress;

/**
 * Tracks the phases of the latest running refactoring. The progress is read by other threads while the refactoring is
 * running.
 */
class RefactoringProgressTracker {
   private final AtomicReference<RefactoringProgress> progress = new AtomicReference<>( RefactoringProgress.IDLE );

   /**
    * Runs a refactoring and tracks its progress until it is finished.
    *
    * @param steps the number of phases the refactoring passes
    * @param refactoring the refactoring, which reports its phases with {@link #enter(RefactoringPhase, int)}
    * @return the result of the refactoring
    */
   <T> T track( final int steps, final Supplier<T> refactoring ) {
      progress.set( new RefactoringProgress( true, null, 0, steps, 0 ) );
      try {
         return refactoring.get();
      } finally {
         progress.set( RefactoringProgress.IDLE );
      }
   }

   /**
    * Reports that the running refactoring entered the next phase.
    *
    * @param phase the phase
    * @param files the number of files that are processed in the phase
    */
   void enter( final RefactoringPhase phase, final int files ) {
      progress.updateAndGet( current -> current.running()
            ? new RefactoringProgress( true, phase, Math.min( current.step() + 1, current.steps() ), current.steps(), files )
            : current );
   }

   RefactoringProgress current() {
      return progress.get();
   }
}
