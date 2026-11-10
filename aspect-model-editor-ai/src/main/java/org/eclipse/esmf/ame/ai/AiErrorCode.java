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

import io.micronaut.http.HttpStatus;

/**
 * The stable error codes of the AI assistant, see the REST contract.
 */
public enum AiErrorCode {
   INVALID_KEY( "invalid_key", HttpStatus.BAD_REQUEST ),
   QUOTA_EXCEEDED( "quota_exceeded", HttpStatus.TOO_MANY_REQUESTS ),
   OFFLINE( "offline", HttpStatus.SERVICE_UNAVAILABLE ),
   TIMEOUT( "timeout", HttpStatus.GATEWAY_TIMEOUT ),
   // 401 is avoided on purpose: the frontend treats it as an invalid AME session token
   NOT_AUTHENTICATED( "not_authenticated", HttpStatus.BAD_REQUEST ),
   MODEL_NOT_FOUND( "model_not_found", HttpStatus.NOT_FOUND ),
   PROVIDER_ERROR( "provider_error", HttpStatus.BAD_GATEWAY ),
   LOCAL_ONLY( "local_only", HttpStatus.FORBIDDEN ),
   READ_ONLY( "read_only", HttpStatus.FORBIDDEN ),
   ATTACHMENT_UNSUPPORTED( "attachment_unsupported", HttpStatus.BAD_REQUEST ),
   ATTACHMENT_TOO_LARGE( "attachment_too_large", HttpStatus.REQUEST_ENTITY_TOO_LARGE ),
   ATTACHMENT_UNREADABLE( "attachment_unreadable", HttpStatus.BAD_REQUEST ),
   ATTACHMENT_EMPTY( "attachment_empty", HttpStatus.BAD_REQUEST );

   private final String code;
   private final HttpStatus httpStatus;

   AiErrorCode( final String code, final HttpStatus httpStatus ) {
      this.code = code;
      this.httpStatus = httpStatus;
   }

   /**
    * @return the code sent to the client
    */
   public String code() {
      return code;
   }

   /**
    * @return the HTTP status of the error
    */
   public HttpStatus httpStatus() {
      return httpStatus;
   }
}
