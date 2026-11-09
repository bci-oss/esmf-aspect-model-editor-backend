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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Function;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

/**
 * Distributes {@link WorkspaceEvent}s to any number of subscribers, e.g. server-sent event streams.
 * <p>
 * Every subscriber gets its own buffered publisher, so a slow subscriber neither blocks the publishing thread nor
 * other subscribers; events that do not fit into its buffer are dropped for that subscriber only. Publishers whose
 * subscriber cancelled are discarded on the next event.
 */
@Singleton
public class WorkspaceEventBroadcaster {
   private final Set<Channel<?>> channels = ConcurrentHashMap.newKeySet();

   /**
    * Creates a publisher that emits {@link WorkspaceEvent#ready()} and then every published event.
    *
    * @param mapper converts the events into the type expected by the subscriber
    * @param <T> the type expected by the subscriber
    * @return the publisher; every subscription receives its own stream
    */
   public <T> Flow.Publisher<T> subscribe( final Function<WorkspaceEvent, T> mapper ) {
      return subscriber -> {
         final Channel<T> channel = new Channel<>( new SubmissionPublisher<>(), mapper );
         channel.publisher().subscribe( subscriber );
         channel.offer( WorkspaceEvent.ready() );
         channels.add( channel );
      };
   }

   /**
    * Sends an event to all current subscribers.
    *
    * @param event the event
    */
   public void publish( final WorkspaceEvent event ) {
      channels.removeIf( channel -> {
         if ( channel.publisher().getNumberOfSubscribers() == 0 ) {
            channel.publisher().close();
            return true;
         }
         channel.offer( event );
         return false;
      } );
   }

   /**
    * @return the number of open subscriptions
    */
   public int subscriberCount() {
      return (int) channels.stream().filter( channel -> channel.publisher().getNumberOfSubscribers() > 0 ).count();
   }

   /**
    * Completes all subscriptions, e.g. when the server shuts down.
    */
   @PreDestroy
   public void close() {
      channels.forEach( channel -> channel.publisher().close() );
      channels.clear();
   }

   private record Channel<T>( SubmissionPublisher<T> publisher, Function<WorkspaceEvent, T> mapper ) {
      void offer( final WorkspaceEvent event ) {
         publisher.offer( mapper.apply( event ), ( subscriber, dropped ) -> false );
      }
   }
}
