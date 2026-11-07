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
 * Exception thrown when a refactoring or its revert cannot be performed in the current state of the workspaces, e.g.
 * because a target file already exists, a workspace file is unreadable or a file was changed after the refactoring.
 * Results in HTTP 409 Conflict response.
 */
public class RefactoringException extends AspectModelEditorException {
   @Serial
   private static final long serialVersionUID = 1L;

   public RefactoringException( final String message ) {
      super( message, HttpStatus.CONFLICT.getCode() );
   }

   public RefactoringException( final String message, final Throwable cause ) {
      super( message, cause, HttpStatus.CONFLICT.getCode() );
   }
}
