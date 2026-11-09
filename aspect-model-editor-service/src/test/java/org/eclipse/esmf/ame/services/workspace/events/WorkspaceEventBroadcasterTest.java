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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;

import org.eclipse.esmf.ame.services.models.workspace.WorkspaceFilesChanged;

import org.junit.jupiter.api.Test;

class WorkspaceEventBroadcasterTest {
   private final WorkspaceEventBroadcaster broadcaster = new WorkspaceEventBroadcaster();

   @Test
   void everySubscriberFirstReceivesReadyAndThenThePublishedEvents() throws InterruptedException {
      final RecordingSubscriber<WorkspaceEvent> first = subscribe( Function.identity() );
      final RecordingSubscriber<WorkspaceEvent> second = subscribe( Function.identity() );

      broadcaster.publish( WorkspaceEvent.filesChanged( emptyChanges() ) );

      for ( final RecordingSubscriber<WorkspaceEvent> subscriber : List.of( first, second ) ) {
         subscriber.assertNext( WorkspaceEvent.ready() );
         subscriber.assertNext( WorkspaceEvent.filesChanged( emptyChanges() ) );
      }
      assertEquals( 2, broadcaster.subscriberCount() );
   }

   @Test
   void eventsAreMappedForTheSubscriber() throws InterruptedException {
      final RecordingSubscriber<String> subscriber = subscribe( WorkspaceEvent::name );

      broadcaster.publish( WorkspaceEvent.heartbeat() );

      subscriber.assertNext( WorkspaceEvent.READY );
      subscriber.assertNext( WorkspaceEvent.HEARTBEAT );
   }

   @Test
   void cancelledSubscriptionIsDiscarded() throws InterruptedException {
      final RecordingSubscriber<WorkspaceEvent> cancelled = subscribe( Function.identity() );
      final RecordingSubscriber<WorkspaceEvent> active = subscribe( Function.identity() );
      cancelled.assertNext( WorkspaceEvent.ready() );

      cancelled.cancel();
      broadcaster.publish( WorkspaceEvent.heartbeat() );

      cancelled.assertNoMoreItems();
      active.assertNext( WorkspaceEvent.ready() );
      active.assertNext( WorkspaceEvent.heartbeat() );
      assertEquals( 1, broadcaster.subscriberCount() );
   }

   @Test
   void closingCompletesAllSubscriptions() throws InterruptedException {
      final RecordingSubscriber<WorkspaceEvent> subscriber = subscribe( Function.identity() );
      subscriber.assertNext( WorkspaceEvent.ready() );

      broadcaster.close();

      final long deadline = System.nanoTime() + 5_000_000_000L;
      while ( !subscriber.isCompleted() && System.nanoTime() < deadline ) {
         Thread.sleep( 10 );
      }
      assertTrue( subscriber.isCompleted() );
      assertEquals( 0, broadcaster.subscriberCount() );
   }

   private static WorkspaceFilesChanged emptyChanges() {
      return new WorkspaceFilesChanged( List.of(), List.of(), List.of() );
   }

   private <T> RecordingSubscriber<T> subscribe( final Function<WorkspaceEvent, T> mapper ) {
      final RecordingSubscriber<T> subscriber = new RecordingSubscriber<>();
      broadcaster.subscribe( mapper ).subscribe( subscriber );
      return subscriber;
   }
}
