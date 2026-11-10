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

import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.api.model.response.Error;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers AI errors with their stable code: {@code { "code": "invalid_key", "message": "...", "error": {...} }}. The
 * {@code error} object is the usual AME error.
 */
@Singleton
@Produces( MediaType.APPLICATION_JSON )
public class AiExceptionHandler implements ExceptionHandler<AiException, HttpResponse<AiExceptionHandler.AiErrorResponse>> {
   private static final Logger LOG = LoggerFactory.getLogger( AiExceptionHandler.class );

   @Override
   public HttpResponse<AiErrorResponse> handle( final HttpRequest request, final AiException exception ) {
      final HttpStatus status = exception.getCode().httpStatus();
      LOG.info( "AI request {} failed: {} {}", request.getPath(), exception.getCode().code(), exception.getMessage() );
      return HttpResponse.<AiErrorResponse> status( status ).body( new AiErrorResponse( exception.getCode().code(), exception.getMessage(),
            new Error( exception.getMessage(), request.getUri().toString(), status.getCode() ) ) );
   }

   /**
    * The body of an AI error.
    */
   @Serdeable
   public record AiErrorResponse( String code, String message, Error error ) {
   }
}
