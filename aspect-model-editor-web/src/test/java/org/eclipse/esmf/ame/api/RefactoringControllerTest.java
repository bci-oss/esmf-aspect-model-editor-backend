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

package org.eclipse.esmf.ame.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.esmf.ame.exceptions.RefactoringException;
import org.eclipse.esmf.ame.services.models.refactoring.ChangedFile;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringHistoryEntry;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringPhase;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringPreview;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringProgress;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringRequest;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringType;
import org.eclipse.esmf.ame.services.refactoring.RefactoringService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefactoringControllerTest {
   private static final RefactoringRequest RENAME = new RefactoringRequest( RefactoringType.RENAME_ELEMENT,
         "urn:samm:org.example:1.0.0#speed", "velocity", null, null, null, null, null, null, null, null, null );
   private static final ChangedFile MOVEMENT = new ChangedFile( "org.example", "1.0.0", "Movement.ttl",
         "/models/org.example/1.0.0/Movement.ttl", false );
   private static final RefactoringHistoryEntry ENTRY = new RefactoringHistoryEntry( "id", "Rename speed to velocity", 1L, List.of(),
         List.of( MOVEMENT ), List.of() );

   private RefactoringService refactoringService;
   private RefactoringController refactoringController;

   @BeforeEach
   void setUp() {
      refactoringService = mock( RefactoringService.class );
      refactoringController = new RefactoringController( refactoringService );
   }

   @Test
   void testPreview() {
      final RefactoringPreview preview = new RefactoringPreview( "Rename speed to velocity", "report", List.of(), List.of( MOVEMENT ),
            List.of(), List.of() );
      when( refactoringService.preview( RENAME ) ).thenReturn( preview );

      final HttpResponse<RefactoringPreview> response = refactoringController.preview( RENAME );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( preview, response.body() );
   }

   @Test
   void testApply() {
      when( refactoringService.apply( RENAME ) ).thenReturn( ENTRY );

      assertEquals( ENTRY, refactoringController.apply( RENAME ).body() );
   }

   @Test
   void testGetProgress() {
      final RefactoringProgress progress = new RefactoringProgress( true, RefactoringPhase.LOADING, 1, 3, 1200 );
      when( refactoringService.getProgress() ).thenReturn( progress );

      assertEquals( progress, refactoringController.getProgress().body() );
   }

   @Test
   void testGetHistory() {
      when( refactoringService.getHistory() ).thenReturn( List.of( ENTRY ) );

      assertEquals( List.of( ENTRY ), refactoringController.getHistory().body() );
   }

   @Test
   void testRevert() {
      when( refactoringService.revert( "id", true ) ).thenReturn( ENTRY );

      assertEquals( ENTRY, refactoringController.revert( "id", true ).body() );
      verify( refactoringService ).revert( "id", true );
   }

   @Test
   void testRevertConflict() {
      when( refactoringService.revert( "id", false ) ).thenThrow( new RefactoringException( "changed" ) );

      final RefactoringException exception = assertThrows( RefactoringException.class, () -> refactoringController.revert( "id", false ) );
      assertEquals( HttpStatus.CONFLICT.getCode(), exception.getHttpStatusCode() );
   }
}
