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
 * Exception thrown when a plugin cannot handle a call. A call the plugin rejected with a message for the user results in
 * HTTP 422 Unprocessable Entity, an unexpected failure of the plugin in HTTP 500 Internal Server Error.
 */
public class PluginExecutionException extends AspectModelEditorException {
   @Serial
   private static final long serialVersionUID = 1L;

   private PluginExecutionException( final String message, final Throwable cause, final HttpStatus status ) {
      super( message, cause, status.getCode() );
   }

   /**
    * Creates the exception for a call the plugin rejected, e.g. because of an invalid file.
    *
    * @param message the message of the plugin, shown to the user
    * @param cause the exception thrown by the plugin
    * @return the exception
    */
   public static PluginExecutionException rejected( final String message, final Throwable cause ) {
      return new PluginExecutionException( message, cause, HttpStatus.UNPROCESSABLE_ENTITY );
   }

   /**
    * Creates the exception for an unexpected failure of the plugin.
    *
    * @param message a description of the failed call
    * @param cause the exception thrown by the plugin
    * @return the exception
    */
   public static PluginExecutionException failed( final String message, final Throwable cause ) {
      return new PluginExecutionException( message, cause, HttpStatus.INTERNAL_SERVER_ERROR );
   }
}
