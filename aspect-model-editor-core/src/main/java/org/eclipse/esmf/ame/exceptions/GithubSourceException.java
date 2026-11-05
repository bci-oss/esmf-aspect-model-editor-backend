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
 * Exception thrown when a GitHub source cannot be checked or synchronized, e.g. because GitHub is not reachable,
 * the repository does not exist or the access token was rejected. Results in HTTP 502 Bad Gateway by default.
 */
public class GithubSourceException extends AspectModelEditorException {
   @Serial
   private static final long serialVersionUID = 1L;

   public GithubSourceException( final String message ) {
      super( message, HttpStatus.BAD_GATEWAY.getCode() );
   }

   public GithubSourceException( final String message, final Throwable cause ) {
      super( message, cause, HttpStatus.BAD_GATEWAY.getCode() );
   }
}
