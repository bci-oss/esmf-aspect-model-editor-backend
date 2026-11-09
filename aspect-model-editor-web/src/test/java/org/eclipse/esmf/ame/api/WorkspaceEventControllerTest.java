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
package org.eclipse.esmf.ame.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.esmf.ame.services.models.workspace.WorkspaceFilesChanged;
import org.eclipse.esmf.ame.services.workspace.events.WorkspaceEvent;
import org.eclipse.esmf.ame.services.workspace.events.WorkspaceEventBroadcaster;

import io.micronaut.http.sse.Event;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class WorkspaceEventControllerTest {
   @Test
   void workspaceEventsBecomeNamedServerSentEvents() {
      final WorkspaceFilesChanged changes = new WorkspaceFilesChanged( List.of(), List.of(), List.of() );

      final Event<Object> event = WorkspaceEventController.toServerSentEvent( WorkspaceEvent.filesChanged( changes ) );

      assertEquals( "files-changed", event.getName() );
      assertSame( changes, event.getData() );
   }

   @Test
   void streamStartsWithTheReadyEvent() throws Exception {
      final WorkspaceEventBroadcaster broadcaster = new WorkspaceEventBroadcaster();
      final CompletableFuture<Event<Object>> first = new CompletableFuture<>();

      new WorkspaceEventController( broadcaster ).streamEvents().subscribe( new Subscriber<>() {
         @Override
         public void onSubscribe( final Subscription subscription ) {
            subscription.request( 1 );
         }

         @Override
         public void onNext( final Event<Object> event ) {
            first.complete( event );
         }

         @Override
         public void onError( final Throwable throwable ) {
            first.completeExceptionally( throwable );
         }

         @Override
         public void onComplete() {
         }
      } );

      final Event<Object> ready = first.get( 5, TimeUnit.SECONDS );
      assertNotNull( ready );
      assertEquals( "ready", ready.getName() );
      assertEquals( Map.of(), ready.getData() );
      broadcaster.close();
   }
}
