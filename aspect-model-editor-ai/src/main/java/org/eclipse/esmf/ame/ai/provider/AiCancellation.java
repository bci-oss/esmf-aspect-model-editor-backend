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

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cancels a running chat turn, e.g. when the user presses stop, closes the stream or the timeout elapses.
 */
public final class AiCancellation {
   private final List<Runnable> callbacks = new CopyOnWriteArrayList<>();
   private volatile boolean cancelled;

   /**
    * Cancels the turn and runs the registered callbacks once.
    */
   public void cancel() {
      if ( cancelled ) {
         return;
      }
      cancelled = true;
      callbacks.forEach( AiCancellation::runQuietly );
   }

   /**
    * @return whether the turn was cancelled
    */
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * @throws CancellationException if cancelled
    */
   public void throwIfCancelled() {
      if ( cancelled ) {
         throw new CancellationException( "The AI request was cancelled." );
      }
   }

   /**
    * Registers a callback run on cancellation; runs it immediately if already cancelled.
    */
   public void onCancel( final Runnable callback ) {
      callbacks.add( callback );
      if ( cancelled ) {
         runQuietly( callback );
      }
   }

   private static void runQuietly( final Runnable callback ) {
      try {
         callback.run();
      } catch ( final RuntimeException ignored ) {
         // cancelling is best effort
      }
   }
}
