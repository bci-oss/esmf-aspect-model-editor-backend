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
package org.eclipse.esmf.ame.services.workspace.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Collects the items of a {@link Flow.Publisher} for assertions in tests.
 */
final class RecordingSubscriber<T> implements Flow.Subscriber<T> {
   private static final Duration TIMEOUT = Duration.ofSeconds( 5 );

   private final BlockingQueue<T> items = new LinkedBlockingQueue<>();
   private volatile Flow.Subscription subscription;
   private volatile boolean completed;

   @Override
   public void onSubscribe( final Flow.Subscription newSubscription ) {
      subscription = newSubscription;
      newSubscription.request( Long.MAX_VALUE );
   }

   @Override
   public void onNext( final T item ) {
      items.add( item );
   }

   @Override
   public void onError( final Throwable throwable ) {
      completed = true;
   }

   @Override
   public void onComplete() {
      completed = true;
   }

   T next() throws InterruptedException {
      final T item = items.poll( TIMEOUT.toMillis(), TimeUnit.MILLISECONDS );
      assertTrue( item != null, "expected another item within " + TIMEOUT );
      return item;
   }

   void assertNoMoreItems() throws InterruptedException {
      assertNull( items.poll( 200, TimeUnit.MILLISECONDS ) );
   }

   void cancel() {
      subscription.cancel();
   }

   boolean isCompleted() {
      return completed;
   }

   void assertNext( final T expected ) throws InterruptedException {
      assertEquals( expected, next() );
   }
}
