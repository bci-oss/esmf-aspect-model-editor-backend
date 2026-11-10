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
package org.eclipse.esmf.ame.ai;

import org.eclipse.esmf.ame.ai.model.AiError;
import org.eclipse.esmf.ame.exceptions.AspectModelEditorException;

/**
 * An error of the AI assistant with a stable {@link AiErrorCode}. The message never contains an API key.
 */
public class AiException extends AspectModelEditorException {
   private final AiErrorCode code;

   /**
    * Creates an exception.
    *
    * @param code the error code
    * @param message the message shown to the user
    */
   public AiException( final AiErrorCode code, final String message ) {
      super( message, code.httpStatus().getCode() );
      this.code = code;
   }

   /**
    * Creates an exception.
    *
    * @param code the error code
    * @param message the message shown to the user
    * @param cause the cause
    */
   public AiException( final AiErrorCode code, final String message, final Throwable cause ) {
      super( message, cause, code.httpStatus().getCode() );
      this.code = code;
   }

   /**
    * @return the error code
    */
   public AiErrorCode getCode() {
      return code;
   }

   /**
    * @return the error as sent to the client
    */
   public AiError toError() {
      return new AiError( code.code(), getMessage() );
   }
}
