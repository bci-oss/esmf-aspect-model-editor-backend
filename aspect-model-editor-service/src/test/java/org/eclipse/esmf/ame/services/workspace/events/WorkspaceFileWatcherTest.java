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

import static org.eclipse.esmf.ame.services.workspace.WorkspaceTestFiles.consumerB;
import static org.eclipse.esmf.ame.services.workspace.WorkspaceTestFiles.providerA;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.eclipse.esmf.ame.services.models.refactoring.ChangedFile;
import org.eclipse.esmf.ame.services.models.workspace.WorkspaceFilesChanged;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceFileWatcherTest {
   @TempDir
   Path workspace;

   private final WorkspaceEventBroadcaster broadcaster = new WorkspaceEventBroadcaster();
   private final RecordingSubscriber<WorkspaceEvent> events = new RecordingSubscriber<>();
   private WorkspaceFileWatcher watcher;
   private Path provider;

   @BeforeEach
   void setUp() throws InterruptedException {
      provider = providerA( workspace );
      broadcaster.subscribe( Function.identity() ).subscribe( events );
      events.assertNext( WorkspaceEvent.ready() );
   }

   @AfterEach
   void tearDown() {
      if ( watcher != null ) {
         watcher.stop();
      }
      broadcaster.close();
   }

   @Test
   void publishesExternalChanges() throws IOException, InterruptedException {
      watcher = manualWatcher();
      watcher.poll();
      events.assertNext( filesChanged( List.of( changed( provider, "org.example.a", "Provider.ttl" ) ), List.of() ) );

      Files.writeString( provider, Files.readString( provider ) + "\n# edited in VS Code\n" );
      watcher.poll();

      events.assertNext( filesChanged( List.of(), List.of( changed( provider, "org.example.a", "Provider.ttl" ) ) ) );
   }

   @Test
   void publishesNothingIfNothingChanged() throws InterruptedException {
      watcher = manualWatcher();
      watcher.poll();
      events.next();

      watcher.poll();

      events.assertNoMoreItems();
   }

   @Test
   void ownWritesAreNotPublished() throws IOException, InterruptedException {
      watcher = manualWatcher();
      watcher.absorb();

      watcher.beginOwnWrite();
      Files.writeString( provider, Files.readString( provider ) + "\n# saved by the editor\n" );
      watcher.poll();
      final Path consumer = consumerB( workspace );
      watcher.endOwnWrite();
      watcher.poll();

      events.assertNoMoreItems();

      Files.delete( consumer );
      watcher.poll();
      events.assertNext( new WorkspaceEvent( WorkspaceEvent.FILES_CHANGED,
            new WorkspaceFilesChanged( List.of(), List.of(), List.of( changed( consumer, "org.example.b", "Consumer.ttl" ) ) ) ) );
   }

   @Test
   void overlappingOwnWritesAreAbsorbedAfterTheLastOneFinished() throws IOException, InterruptedException {
      watcher = manualWatcher();
      watcher.absorb();

      watcher.beginOwnWrite();
      watcher.beginOwnWrite();
      Files.writeString( provider, Files.readString( provider ) + "\n# first request\n" );
      watcher.endOwnWrite();
      watcher.poll();
      Files.writeString( provider, Files.readString( provider ) + "\n# second request\n" );
      watcher.endOwnWrite();
      watcher.poll();

      events.assertNoMoreItems();
   }

   @Test
   void failingScanDoesNotStopTheWatcher() throws IOException, InterruptedException {
      final WorkspaceReferenceService referenceService = new WorkspaceReferenceService( workspace );
      final boolean[] failing = { true };
      watcher = new WorkspaceFileWatcher( new WorkspaceChangeDetector( () -> {
         if ( failing[0] ) {
            throw new IllegalStateException( "configuration unavailable" );
         }
         return List.of( workspace );
      }, referenceService::listWorkspaceFiles ), broadcaster, Duration.ofDays( 1 ) );

      watcher.poll();
      failing[0] = false;
      watcher.poll();

      events.assertNext( filesChanged( List.of( changed( provider, "org.example.a", "Provider.ttl" ) ), List.of() ) );
   }

   @Test
   void scheduledPollingPublishesChangesInTheBackground() throws IOException, InterruptedException {
      final WorkspaceReferenceService referenceService = new WorkspaceReferenceService( workspace );
      watcher = new WorkspaceFileWatcher( referenceService, broadcaster, Duration.ofMillis( 50 ) );
      watcher.start();

      Files.writeString( provider, Files.readString( provider ) + "\n# edited\n" );

      events.assertNext( filesChanged( List.of(), List.of( changed( provider, "org.example.a", "Provider.ttl" ) ) ) );
   }

   private WorkspaceFileWatcher manualWatcher() {
      final WorkspaceReferenceService referenceService = new WorkspaceReferenceService( workspace );
      return new WorkspaceFileWatcher( new WorkspaceChangeDetector( () -> List.of( workspace ), referenceService::listWorkspaceFiles ),
            broadcaster, Duration.ofDays( 1 ) );
   }

   private static WorkspaceEvent filesChanged( final List<ChangedFile> created, final List<ChangedFile> modified ) {
      return WorkspaceEvent.filesChanged( new WorkspaceFilesChanged( created, modified, List.of() ) );
   }

   private static ChangedFile changed( final Path file, final String namespace, final String fileName ) {
      return new ChangedFile( namespace, "1.0.0", fileName, file.toAbsolutePath().normalize().toString(), false );
   }
}
