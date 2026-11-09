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
package org.eclipse.esmf.ame.filter;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.esmf.ame.services.workspace.events.WorkspaceFileWatcher;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.FilterContinuation;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class OwnWriteTrackingFilterTest {
   private final WorkspaceFileWatcher watcher = mock( WorkspaceFileWatcher.class );
   private final OwnWriteTrackingFilter filter = new OwnWriteTrackingFilter( watcher );

   @SuppressWarnings( "unchecked" )
   private final FilterContinuation<MutableHttpResponse<?>> continuation = mock( FilterContinuation.class );

   @Test
   void requestRunsBetweenBeginAndEndOfTheOwnWrite() {
      final MutableHttpResponse<?> response = HttpResponse.ok();
      when( continuation.proceed() ).thenAnswer( invocation -> response );

      filter.trackOwnWrite( continuation );

      final InOrder order = inOrder( watcher, continuation );
      order.verify( watcher ).beginOwnWrite();
      order.verify( continuation ).proceed();
      order.verify( watcher ).endOwnWrite();
   }

   @Test
   void ownWriteEndsEvenIfTheRequestFails() {
      final IllegalStateException failure = new IllegalStateException( "disk full" );
      when( continuation.proceed() ).thenThrow( failure );

      assertSame( failure, assertThrows( IllegalStateException.class, () -> filter.trackOwnWrite( continuation ) ) );

      final InOrder order = inOrder( watcher );
      order.verify( watcher ).beginOwnWrite();
      order.verify( watcher ).endOwnWrite();
   }
}
