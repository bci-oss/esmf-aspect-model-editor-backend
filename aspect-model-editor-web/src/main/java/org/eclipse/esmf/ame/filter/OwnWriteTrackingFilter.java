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

import org.eclipse.esmf.ame.services.workspace.events.WorkspaceFileWatcher;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/**
 * Tells the {@link WorkspaceFileWatcher} about requests that may write model files (saving, refactoring, deleting,
 * importing, migrating, ...), so that the files the backend writes itself are not reported as external changes.
 */
@ServerFilter( value = ServerFilter.MATCH_ALL_PATTERN, methods = { HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE } )
public class OwnWriteTrackingFilter implements Ordered {
   private final WorkspaceFileWatcher watcher;

   public OwnWriteTrackingFilter( final WorkspaceFileWatcher watcher ) {
      this.watcher = watcher;
   }

   /**
    * Runs after the authentication, so rejected requests are not tracked.
    */
   @Override
   public int getOrder() {
      return ServerFilterPhase.SECURITY.after();
   }

   @RequestFilter
   @ExecuteOn( TaskExecutors.BLOCKING )
   public void trackOwnWrite( final FilterContinuation<MutableHttpResponse<?>> continuation ) {
      watcher.beginOwnWrite();
      try {
         continuation.proceed();
      } finally {
         watcher.endOwnWrite();
      }
   }
}
