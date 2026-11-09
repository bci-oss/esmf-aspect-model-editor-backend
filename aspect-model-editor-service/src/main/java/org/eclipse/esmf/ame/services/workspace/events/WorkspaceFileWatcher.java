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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.esmf.ame.services.models.workspace.WorkspaceFilesChanged;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notices changes that other programs (e.g. a text editor) make to the model files of the local workspaces and
 * publishes them as {@link WorkspaceEvent#FILES_CHANGED} events.
 * <p>
 * The workspaces are polled instead of using {@link java.nio.file.WatchService}: the JDK only polls on macOS anyway, on
 * Windows watched directories are kept open, which can block renaming or deleting a namespace, and a poll is cheap
 * since unchanged files are recognized by their attributes. Read-only sources (e.g. GitHub caches) are not watched.
 * <p>
 * Changes that the backend makes itself must not be reported. Requests that may write files are therefore wrapped in
 * {@link #beginOwnWrite()} and {@link #endOwnWrite()}: no changes are published while such a request runs, and when the
 * last one finished, the current state is taken over silently.
 */
@Context
public class WorkspaceFileWatcher {
   private static final Logger LOG = LoggerFactory.getLogger( WorkspaceFileWatcher.class );
   private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds( 15 );

   private final WorkspaceChangeDetector detector;
   private final WorkspaceEventBroadcaster broadcaster;
   private final Duration interval;
   private final ReentrantLock lock = new ReentrantLock();
   private int ownWrites;
   private ScheduledExecutorService scheduler;

   @Inject
   public WorkspaceFileWatcher( final WorkspaceReferenceService referenceService, final WorkspaceEventBroadcaster broadcaster,
         @Value( "${ame.workspace-watch.interval:1s}" ) final Duration interval ) {
      this( new WorkspaceChangeDetector( referenceService::getWritableRoots, referenceService::listWorkspaceFiles ), broadcaster,
            interval );
   }

   WorkspaceFileWatcher( final WorkspaceChangeDetector detector, final WorkspaceEventBroadcaster broadcaster, final Duration interval ) {
      this.detector = detector;
      this.broadcaster = broadcaster;
      this.interval = interval;
   }

   @PostConstruct
   void start() {
      // Builds the index, so that every change after the startup is reported. Only attributes are read and new files hashed.
      absorb();
      scheduler = Executors.newSingleThreadScheduledExecutor( Thread.ofPlatform().daemon().name( "ame-workspace-watcher" ).factory() );
      scheduler.scheduleWithFixedDelay( this::poll, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS );
      scheduler.scheduleAtFixedRate( () -> broadcaster.publish( WorkspaceEvent.heartbeat() ), HEARTBEAT_INTERVAL.toMillis(),
            HEARTBEAT_INTERVAL.toMillis(), TimeUnit.MILLISECONDS );
   }

   @PreDestroy
   void stop() {
      if ( scheduler != null ) {
         scheduler.shutdownNow();
      }
   }

   /**
    * Marks the start of an operation of the backend that may write model files.
    */
   public void beginOwnWrite() {
      lock.lock();
      try {
         ownWrites++;
      } finally {
         lock.unlock();
      }
   }

   /**
    * Marks the end of an operation started with {@link #beginOwnWrite()}. After the last running operation, the
    * current state of the workspaces is taken over without publishing it.
    */
   public void endOwnWrite() {
      lock.lock();
      try {
         ownWrites = Math.max( 0, ownWrites - 1 );
         if ( ownWrites == 0 ) {
            scanSafely();
         }
      } finally {
         lock.unlock();
      }
   }

   /**
    * Publishes the changes since the previous scan, unless the backend is writing itself.
    */
   void poll() {
      lock.lock();
      try {
         if ( ownWrites == 0 ) {
            scanSafely().ifPresent( changes -> broadcaster.publish( WorkspaceEvent.filesChanged( changes ) ) );
         }
      } finally {
         lock.unlock();
      }
   }

   /**
    * Takes over the current state of the workspaces without publishing it.
    */
   void absorb() {
      lock.lock();
      try {
         scanSafely();
      } finally {
         lock.unlock();
      }
   }

   private Optional<WorkspaceFilesChanged> scanSafely() {
      try {
         return detector.scan().filter( changes -> !changes.isEmpty() );
      } catch ( final RuntimeException e ) {
         // An exception must not end the scheduled polling.
         LOG.warn( "Could not scan the workspaces for changes", e );
         return Optional.empty();
      }
   }
}
