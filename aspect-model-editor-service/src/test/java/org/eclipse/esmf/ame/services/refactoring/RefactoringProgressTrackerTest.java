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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.esmf.ame.services.models.refactoring.RefactoringPhase;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringProgress;

import org.junit.jupiter.api.Test;

class RefactoringProgressTrackerTest {
   private final RefactoringProgressTracker tracker = new RefactoringProgressTracker();

   @Test
   void reportsThePhasesOfTheRunningRefactoring() {
      final List<RefactoringProgress> observed = new ArrayList<>();

      final String result = tracker.track( 2, () -> {
         observed.add( tracker.current() );
         tracker.enter( RefactoringPhase.LOADING, 1200 );
         observed.add( tracker.current() );
         tracker.enter( RefactoringPhase.PLANNING, 1200 );
         observed.add( tracker.current() );
         return "done";
      } );

      assertEquals( "done", result );
      assertEquals( List.of(
            new RefactoringProgress( true, null, 0, 2, 0 ),
            new RefactoringProgress( true, RefactoringPhase.LOADING, 1, 2, 1200 ),
            new RefactoringProgress( true, RefactoringPhase.PLANNING, 2, 2, 1200 ) ), observed );
      assertEquals( RefactoringProgress.IDLE, tracker.current() );
   }

   @Test
   void isIdleAfterAFailedRefactoring() {
      assertThrows( IllegalStateException.class, () -> tracker.track( 1, () -> {
         tracker.enter( RefactoringPhase.LOADING, 3 );
         throw new IllegalStateException( "failed" );
      } ) );

      assertEquals( RefactoringProgress.IDLE, tracker.current() );
   }

   @Test
   void ignoresPhasesOutsideOfARefactoring() {
      tracker.enter( RefactoringPhase.WRITING, 3 );

      assertEquals( RefactoringProgress.IDLE, tracker.current() );
   }

   @Test
   void neverReportsMoreStepsThanAnnounced() {
      tracker.track( 1, () -> {
         tracker.enter( RefactoringPhase.LOADING, 1 );
         tracker.enter( RefactoringPhase.PLANNING, 1 );
         assertEquals( new RefactoringProgress( true, RefactoringPhase.PLANNING, 1, 1, 1 ), tracker.current() );
         return null;
      } );
   }
}
