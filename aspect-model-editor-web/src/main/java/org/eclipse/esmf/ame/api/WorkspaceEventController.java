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

import org.eclipse.esmf.ame.services.workspace.events.WorkspaceEvent;
import org.eclipse.esmf.ame.services.workspace.events.WorkspaceEventBroadcaster;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.sse.Event;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;

/**
 * Streams changes of the workspaces as server-sent events, e.g. model files edited in another program.
 */
@Controller( "workspaces/events" )
public class WorkspaceEventController {
   private final WorkspaceEventBroadcaster broadcaster;

   public WorkspaceEventController( final WorkspaceEventBroadcaster broadcaster ) {
      this.broadcaster = broadcaster;
   }

   /**
    * Opens an event stream. It starts with a {@value WorkspaceEvent#READY} event, followed by
    * {@value WorkspaceEvent#FILES_CHANGED} events and periodic {@value WorkspaceEvent#HEARTBEAT} events.
    *
    * @return the event stream
    */
   @Get( produces = MediaType.TEXT_EVENT_STREAM )
   public Publisher<Event<Object>> streamEvents() {
      return FlowAdapters.toPublisher( broadcaster.subscribe( WorkspaceEventController::toServerSentEvent ) );
   }

   static Event<Object> toServerSentEvent( final WorkspaceEvent event ) {
      return Event.of( event.data() ).name( event.name() );
   }
}
