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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.api.model.response.Error;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

class AiExceptionHandlerTest {
   private final AiExceptionHandler handler = new AiExceptionHandler();

   @Test
   void respondsWithTheCodeMessageAndAmeError() {
      final HttpResponse<AiExceptionHandler.AiErrorResponse> response = handler.handle(
            HttpRequest.POST( URI.create( "/ame/api/ai/providers/test?x=1" ), "" ),
            new AiException( AiErrorCode.INVALID_KEY, "The API key was rejected by openai." ) );

      assertEquals( HttpStatus.BAD_REQUEST, response.getStatus() );
      assertEquals( new AiExceptionHandler.AiErrorResponse( "invalid_key", "The API key was rejected by openai.",
            new Error( "The API key was rejected by openai.", "/ame/api/ai/providers/test?x=1", 400 ) ), response.body() );
   }

   @Test
   void usesTheHttpStatusOfEveryCode() {
      for ( final AiErrorCode code : AiErrorCode.values() ) {
         assertResponse( code );
      }
   }

   private void assertResponse( final AiErrorCode code ) {
      final HttpResponse<AiExceptionHandler.AiErrorResponse> response = handler.handle( HttpRequest.GET( "/ame/api/ai/models" ),
            new AiException( code, "failed", new IllegalStateException( "cause" ) ) );

      assertEquals( code.httpStatus(), response.getStatus() );
      final AiExceptionHandler.AiErrorResponse body = response.body();
      assertNotNull( body );
      assertEquals( code.code(), body.code() );
      assertEquals( "failed", body.message() );
      assertEquals( code.httpStatus().getCode(), body.error().code() );
      assertEquals( "/ame/api/ai/models", body.error().path() );
   }
}
