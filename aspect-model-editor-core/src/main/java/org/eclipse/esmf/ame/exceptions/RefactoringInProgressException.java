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

package org.eclipse.esmf.ame.exceptions;

import java.io.Serial;

import io.micronaut.http.HttpStatus;

/**
 * Exception thrown when a refactoring is requested while another one is still being written.
 * Results in HTTP 423 Locked response.
 */
public class RefactoringInProgressException extends AspectModelEditorException {
   @Serial
   private static final long serialVersionUID = 1L;

   public RefactoringInProgressException() {
      super( "Another refactoring is in progress. Please try again in a moment.", HttpStatus.LOCKED.getCode() );
   }
}
