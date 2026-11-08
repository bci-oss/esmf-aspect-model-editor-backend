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
 * Exception thrown when a plugin operation is not possible in the current state, e.g. installing a version of a plugin
 * which is currently running or calling a plugin which is not active. Results in HTTP 409 Conflict.
 */
public class PluginConflictException extends AspectModelEditorException {
   @Serial
   private static final long serialVersionUID = 1L;

   public PluginConflictException( final String message ) {
      super( message, HttpStatus.CONFLICT.getCode() );
   }
}
