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
 * Exception thrown when a plugin archive cannot be installed because it is not a valid plugin, e.g. because the
 * {@code plugin.json} is missing or invalid or the archive contains unsafe paths. Results in HTTP 400 Bad Request.
 */
public class InvalidPluginException extends AspectModelEditorException {
   @Serial
   private static final long serialVersionUID = 1L;

   public InvalidPluginException( final String message ) {
      super( message, HttpStatus.BAD_REQUEST.getCode() );
   }

   public InvalidPluginException( final String message, final Throwable cause ) {
      super( message, cause, HttpStatus.BAD_REQUEST.getCode() );
   }
}
