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
package org.eclipse.esmf.ame.ai.provider;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import io.micronaut.core.annotation.Nullable;

/**
 * Maps errors of the providers to the stable error codes. Messages never contain the API key.
 */
public final class AiErrors {
   private static final int MAX_DETAIL_LENGTH = 300;

   private AiErrors() {
   }

   /**
    * @param throwable the error
    * @param settings the provider settings, used to remove the API key from messages
    * @return the error with code; {@link CancellationException}s are returned unchanged
    */
   public static RuntimeException map( final Throwable throwable, final @Nullable AiProviderSettings settings ) {
      final String provider = settings == null ? "the AI provider" : settings.kind().id();
      for ( Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause() ) {
         if ( t instanceof final AiException aiException ) {
            return aiException;
         }
         if ( t instanceof final CancellationException cancellation ) {
            return cancellation;
         }
         final AiErrorCode code = codeOf( t );
         if ( code != null ) {
            return new AiException( code, message( code, provider, t, settings ) );
         }
      }
      return new AiException( AiErrorCode.PROVIDER_ERROR, message( AiErrorCode.PROVIDER_ERROR, provider, throwable, settings ) );
   }

   private static @Nullable AiErrorCode codeOf( final Throwable t ) {
      if ( t instanceof AuthenticationException ) {
         return AiErrorCode.INVALID_KEY;
      }
      if ( t instanceof RateLimitException ) {
         return AiErrorCode.QUOTA_EXCEEDED;
      }
      if ( t instanceof ModelNotFoundException ) {
         return AiErrorCode.MODEL_NOT_FOUND;
      }
      if ( t instanceof UnresolvedModelServerException || t instanceof ConnectException || t instanceof UnknownHostException
            || t instanceof NoRouteToHostException || t instanceof HttpConnectTimeoutException ) {
         return AiErrorCode.OFFLINE;
      }
      if ( t instanceof dev.langchain4j.exception.TimeoutException || t instanceof HttpTimeoutException
            || t instanceof TimeoutException || t instanceof SocketTimeoutException ) {
         return AiErrorCode.TIMEOUT;
      }
      if ( t instanceof final HttpException httpException ) {
         return codeOfStatus( httpException.statusCode() );
      }
      return null;
   }

   /**
    * @return the error code of an HTTP status of a provider
    */
   public static AiErrorCode codeOfStatus( final int status ) {
      return switch ( status ) {
         case 401, 403 -> AiErrorCode.INVALID_KEY;
         case 429 -> AiErrorCode.QUOTA_EXCEEDED;
         case 404 -> AiErrorCode.MODEL_NOT_FOUND;
         case 408, 504 -> AiErrorCode.TIMEOUT;
         default -> AiErrorCode.PROVIDER_ERROR;
      };
   }

   private static String message( final AiErrorCode code, final String provider, final Throwable t,
         final @Nullable AiProviderSettings settings ) {
      return switch ( code ) {
         case INVALID_KEY -> "The API key was rejected by " + provider + ".";
         case QUOTA_EXCEEDED -> "The quota or rate limit of " + provider + " is exceeded. Please try again later.";
         case MODEL_NOT_FOUND -> "The model was not found at " + provider + ".";
         case OFFLINE -> provider + " cannot be reached. Please check the network connection and the base URL.";
         case TIMEOUT -> provider + " did not answer in time.";
         default -> "Request to " + provider + " failed: " + detail( t, settings );
      };
   }

   private static String detail( final Throwable t, final @Nullable AiProviderSettings settings ) {
      String detail = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
      detail = settings == null ? detail : settings.scrub( detail );
      return detail.length() > MAX_DETAIL_LENGTH ? detail.substring( 0, MAX_DETAIL_LENGTH ) + "…" : detail;
   }
}
