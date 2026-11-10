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
package org.eclipse.esmf.ame.ai.copilot;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;

/**
 * Waits for the asynchronous results of the Copilot SDK and maps their failures to {@link AiException}s.
 */
final class CopilotFutures {
   static final long AWAIT_TIMEOUT_SECONDS = 180;

   private CopilotFutures() {
   }

   /**
    * Waits for the future.
    *
    * @throws AiException with {@link AiErrorCode#TIMEOUT} or {@link AiErrorCode#PROVIDER_ERROR} if the future fails
    * @throws CancellationException if the future was cancelled or the thread was interrupted
    */
   static <T> T await( final CompletableFuture<T> future ) {
      try {
         return future.get( AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS );
      } catch ( final InterruptedException exception ) {
         Thread.currentThread().interrupt();
         throw new CancellationException( "The Copilot request was interrupted." );
      } catch ( final TimeoutException exception ) {
         throw timeout();
      } catch ( final CancellationException exception ) {
         throw exception;
      } catch ( final ExecutionException exception ) {
         final Throwable cause = exception.getCause() == null ? exception : exception.getCause();
         if ( cause instanceof TimeoutException ) {
            throw timeout();
         }
         if ( cause instanceof final CancellationException cancellation ) {
            throw cancellation;
         }
         throw new AiException( AiErrorCode.PROVIDER_ERROR, "GitHub Copilot failed: " + cause.getMessage(), cause );
      }
   }

   /**
    * Sleeps between two polls of the device login.
    *
    * @throws CancellationException if the thread was interrupted
    */
   static void sleep( final long seconds ) {
      try {
         Thread.sleep( seconds * 1000 );
      } catch ( final InterruptedException exception ) {
         Thread.currentThread().interrupt();
         throw new CancellationException( "The GitHub login was cancelled." );
      }
   }

   private static AiException timeout() {
      return new AiException( AiErrorCode.TIMEOUT, "GitHub Copilot did not answer in time." );
   }
}
