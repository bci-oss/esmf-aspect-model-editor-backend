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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AiCancellationTest {
   private final Logger logger = (Logger) LoggerFactory.getLogger( AiCancellation.class );
   private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

   @BeforeEach
   void captureLogs() {
      logs.start();
      logger.addAppender( logs );
   }

   @AfterEach
   void releaseLogs() {
      logger.detachAppender( logs );
      logs.stop();
   }

   @Test
   void isNotCancelledInitially() {
      final AiCancellation cancellation = new AiCancellation();

      assertFalse( cancellation.isCancelled() );
      assertDoesNotThrow( cancellation::throwIfCancelled );
   }

   @Test
   void runsTheCallbacksOnceWhenCancelled() {
      final AiCancellation cancellation = new AiCancellation();
      final AtomicInteger calls = new AtomicInteger();
      cancellation.onCancel( calls::incrementAndGet );

      cancellation.cancel();
      cancellation.cancel();

      assertTrue( cancellation.isCancelled() );
      assertEquals( 1, calls.get() );
      assertThrows( CancellationException.class, cancellation::throwIfCancelled );
   }

   @Test
   void runsCallbacksRegisteredAfterTheCancellationImmediately() {
      final AiCancellation cancellation = new AiCancellation();
      cancellation.cancel();
      final AtomicInteger calls = new AtomicInteger();

      cancellation.onCancel( calls::incrementAndGet );

      assertEquals( 1, calls.get() );
   }

   @Test
   void logsFailingCallbacksAndRunsTheOthers() {
      final AiCancellation cancellation = new AiCancellation();
      final AtomicInteger calls = new AtomicInteger();
      cancellation.onCancel( () -> {
         throw new IllegalStateException( "stream already closed" );
      } );
      cancellation.onCancel( calls::incrementAndGet );

      assertDoesNotThrow( cancellation::cancel );

      assertEquals( 1, calls.get() );
      final List<ILoggingEvent> warnings = logs.list.stream().filter( event -> event.getLevel() == Level.WARN ).toList();
      assertEquals( 1, warnings.size() );
      assertTrue( warnings.getFirst().getFormattedMessage().contains( "stream already closed" ) );
   }

   @Test
   void logsFailingCallbacksRegisteredAfterTheCancellation() {
      final AiCancellation cancellation = new AiCancellation();
      cancellation.cancel();

      assertDoesNotThrow( () -> cancellation.onCancel( () -> {
         throw new IllegalStateException( "late failure" );
      } ) );

      assertTrue( logs.list.stream().anyMatch( event -> event.getFormattedMessage().contains( "late failure" ) ) );
   }
}
