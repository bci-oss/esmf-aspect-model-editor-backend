/*
 * Copyright (c) 2025 Robert Bosch Manufacturing Solutions GmbH
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

import org.eclipse.esmf.ame.api.model.response.Error;
import org.eclipse.esmf.ame.api.model.response.ErrorResponse;
import org.eclipse.esmf.aspectmodel.AspectLoadingException;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global exception handler for all runtime exceptions in the Aspect Model Editor.
 * Converts exceptions into appropriate HTTP responses with proper status codes.
 */
@Singleton
@Produces( "application/json" )
public class GlobalExceptionHandler implements ExceptionHandler<RuntimeException, HttpResponse<?>> {

   private static final Logger LOG = LoggerFactory.getLogger( GlobalExceptionHandler.class );

   @Override
   public HttpResponse<?> handle( final @NonNull HttpRequest request, final @NonNull RuntimeException exception ) {
      final HttpStatus status = determineHttpStatus( exception );
      logException( request, exception, status );

      final ErrorResponse errorResponse = new ErrorResponse(
            new Error(
                  exception.getMessage(),
                  request.getUri().toString(),
                  status.getCode() )
      );

      return HttpResponse.status( status ).body( errorResponse );
   }

   /**
    * Determines the appropriate HTTP status code for the given exception.
    * Uses the built-in status code from AspectModelEditorException, or defaults
    * to CONFLICT for AspectLoadingException and INTERNAL_SERVER_ERROR for others.
    *
    * @param exception the exception to determine status for
    * @return the appropriate HTTP status
    */
   private HttpStatus determineHttpStatus( final RuntimeException exception ) {
      if ( exception instanceof final AspectModelEditorException ameException ) {
         return HttpStatus.valueOf( ameException.getHttpStatusCode() );
      } else if ( exception instanceof AspectLoadingException ) {
         return HttpStatus.CONFLICT;
      } else {
         return HttpStatus.INTERNAL_SERVER_ERROR;
      }
   }

   /**
    * Logs the exception with appropriate level based on HTTP status code.
    * Server errors (5xx) are logged as errors with stack trace,
    * client errors (4xx) are logged as info.
    *
    * @param request the HTTP request that caused the exception
    * @param exception the exception that occurred
    * @param status the HTTP status being returned
    */
   private void logException( final HttpRequest request, final Throwable exception, final HttpStatus status ) {
      if ( status.getCode() >= 500 ) {
         LOG.error( "Server error {} ({}) for request {}: {}",
               exception.getClass().getSimpleName(),
               status.getCode(),
               request.getUri(),
               exception.getMessage(),
               exception );
      } else {
         LOG.info( "Client error {} ({}) for request {}: {}",
               exception.getClass().getSimpleName(),
               status.getCode(),
               request.getUri(),
               exception.getMessage() );
      }
   }
}
