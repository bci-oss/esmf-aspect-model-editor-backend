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
package org.eclipse.esmf.ame.ai.chat;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.esmf.ame.ai.model.chat.AiChatEvent;

/**
 * The events of one chat request for exactly one subscriber. Events are buffered until requested; the stream completes
 * after the terminal event ({@code done} or {@code error}). The chat is started when the subscriber subscribes and is
 * cancelled when the subscriber cancels (e.g. the client closed the connection).
 *
 * @param <T> the type of the published items
 */
public final class AiEventStream<T> implements Flow.Publisher<T> {
   private final ConcurrentLinkedQueue<AiChatEvent> queue = new ConcurrentLinkedQueue<>();
   private final AtomicLong demand = new AtomicLong();
   private final AtomicInteger work = new AtomicInteger();
   private final AtomicBoolean subscribed = new AtomicBoolean();
   private final Function<AiChatEvent, T> mapper;
   private final Consumer<AiEventStream<T>> onStart;
   private final Runnable onCancel;
   private volatile Flow.Subscriber<? super T> subscriber;
   private volatile boolean terminated;
   private volatile boolean finished;

   /**
    * @param mapper maps the events to the published items
    * @param onStart starts producing events; called once on subscription
    * @param onCancel called when the subscriber cancels before the terminal event
    */
   public AiEventStream( final Function<AiChatEvent, T> mapper, final Consumer<AiEventStream<T>> onStart, final Runnable onCancel ) {
      this.mapper = mapper;
      this.onStart = onStart;
      this.onCancel = onCancel;
   }

   @Override
   public void subscribe( final Flow.Subscriber<? super T> newSubscriber ) {
      if ( !subscribed.compareAndSet( false, true ) ) {
         newSubscriber.onSubscribe( new Flow.Subscription() {
            @Override
            public void request( final long n ) {
            }

            @Override
            public void cancel() {
            }
         } );
         newSubscriber.onError( new IllegalStateException( "The chat stream allows only one subscriber" ) );
         return;
      }
      subscriber = newSubscriber;
      newSubscriber.onSubscribe( new Flow.Subscription() {
         @Override
         public void request( final long n ) {
            if ( n <= 0 ) {
               return;
            }
            demand.getAndAccumulate( n, ( current, added ) -> current + added < 0 ? Long.MAX_VALUE : current + added );
            drain();
         }

         @Override
         public void cancel() {
            if ( !finished ) {
               finished = true;
               queue.clear();
               onCancel.run();
            }
         }
      } );
      onStart.accept( this );
   }

   /**
    * Publishes an event. Events after the terminal event are ignored.
    */
   public void emit( final AiChatEvent event ) {
      synchronized ( this ) {
         if ( terminated || finished ) {
            return;
         }
         terminated = event.isTerminal();
         queue.add( event );
      }
      drain();
   }

   /**
    * @return whether the subscriber cancelled the stream before the terminal event
    */
   public boolean isCancelled() {
      return finished && !terminated;
   }

   private void drain() {
      if ( work.getAndIncrement() != 0 ) {
         return;
      }
      do {
         final Flow.Subscriber<? super T> target = subscriber;
         while ( target != null && !finished && demand.get() > 0 ) {
            final AiChatEvent event = queue.poll();
            if ( event == null ) {
               break;
            }
            demand.decrementAndGet();
            target.onNext( mapper.apply( event ) );
            if ( event.isTerminal() ) {
               finished = true;
               target.onComplete();
            }
         }
      } while ( work.decrementAndGet() != 0 );
   }
}
