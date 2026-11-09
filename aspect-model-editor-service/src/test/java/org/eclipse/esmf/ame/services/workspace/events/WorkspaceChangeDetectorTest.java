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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.esmf.ame.services.models.refactoring.ChangedFile;
import org.eclipse.esmf.ame.services.models.workspace.WorkspaceFilesChanged;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceChangeDetectorTest {
   @TempDir
   Path workspace;

   @TempDir
   Path secondWorkspace;

   private final List<Path> roots = new ArrayList<>();
   private WorkspaceReferenceService referenceService;
   private WorkspaceChangeDetector detector;

   @BeforeEach
   void setUp() {
      roots.add( workspace );
      referenceService = new WorkspaceReferenceService( workspace );
      detector = new WorkspaceChangeDetector( () -> List.copyOf( roots ), referenceService::listWorkspaceFiles );
   }

   @Test
   void firstScanReportsAllFilesAsCreated() {
      final Path provider = providerA( workspace );

      assertEquals( changes( List.of( changed( provider, "org.example.a", "Provider.ttl" ) ), List.of(), List.of() ), detector.scan() );
   }

   @Test
   void unchangedWorkspaceReportsNothing() {
      providerA( workspace );
      detector.scan();

      assertTrue( detector.scan().orElseThrow().isEmpty() );
   }

   @Test
   void reportsCreatedModifiedAndRemovedFiles() throws IOException {
      final Path provider = providerA( workspace );
      final Path consumer = consumerB( workspace );
      detector.scan();

      Files.writeString( provider, Files.readString( provider ) + "\n# edited\n" );
      Files.delete( consumer );
      final Path created = workspace.resolve( "org.example.c" ).resolve( "1.0.0" ).resolve( "New.ttl" );
      Files.createDirectories( created.getParent() );
      Files.writeString( created, "# new" );

      assertEquals( changes(
            List.of( changed( created, "org.example.c", "New.ttl" ) ),
            List.of( changed( provider, "org.example.a", "Provider.ttl" ) ),
            List.of( changed( consumer, "org.example.b", "Consumer.ttl" ) ) ), detector.scan() );
   }

   @Test
   void touchingAFileWithoutChangingItsContentIsNoChange() throws IOException {
      final Path provider = providerA( workspace );
      detector.scan();

      Files.writeString( provider, Files.readString( provider ) );
      Files.setLastModifiedTime( provider, FileTime.from( Instant.now().plusSeconds( 60 ) ) );

      assertTrue( detector.scan().orElseThrow().isEmpty() );
   }

   @Test
   void changeWithEqualSizeAndTimestampIsRecognizedOnceTheTimestampDiffers() throws IOException {
      final Path provider = providerA( workspace );
      detector.scan();
      final String content = Files.readString( provider );

      Files.writeString( provider, content.replace( "sharedProperty", "sharedPropertx" ) );
      Files.setLastModifiedTime( provider, FileTime.from( Instant.now().plusSeconds( 120 ) ) );

      assertEquals( List.of( changed( provider, "org.example.a", "Provider.ttl" ) ), detector.scan().orElseThrow().modified() );
   }

   @Test
   void filesOutsideTheStandardLayoutAreIgnored() throws IOException {
      detector.scan();

      Files.writeString( workspace.resolve( "Loose.ttl" ), "# not in a namespace" );
      final Path backup = workspace.resolve( "org.example.a" ).resolve( "1.0.0" ).resolve( "Provider.ttl.bak" );
      Files.createDirectories( backup.getParent() );
      Files.writeString( backup, "# backup" );

      assertTrue( detector.scan().orElseThrow().isEmpty() );
   }

   @Test
   void addedWorkspaceIsPickedUpAndRemovedWorkspaceReportsItsFilesAsRemoved() {
      detector.scan();
      final Path second = providerA( secondWorkspace );

      roots.add( secondWorkspace );
      assertEquals( List.of( changed( second, "org.example.a", "Provider.ttl" ) ), detector.scan().orElseThrow().created() );

      roots.remove( secondWorkspace );
      assertEquals( List.of( changed( second, "org.example.a", "Provider.ttl" ) ), detector.scan().orElseThrow().removed() );
   }

   @Test
   void unreadableWorkspaceKeepsThePreviousState() {
      final Path provider = providerA( workspace );
      final boolean[] failing = { false };
      final WorkspaceChangeDetector flaky = new WorkspaceChangeDetector( () -> List.of( workspace ), root -> {
         if ( failing[0] ) {
            throw new UncheckedIOException( new IOException( "locked" ) );
         }
         return referenceService.listWorkspaceFiles( root );
      } );
      flaky.scan();

      failing[0] = true;
      assertEquals( Optional.empty(), flaky.scan() );

      failing[0] = false;
      assertTrue( flaky.scan().orElseThrow().isEmpty(), "the file must not be reported as removed and created again: " + provider );
   }

   private static ChangedFile changed( final Path file, final String namespace, final String fileName ) {
      return new ChangedFile( namespace, "1.0.0", fileName, file.toAbsolutePath().normalize().toString(), false );
   }

   private static Optional<WorkspaceFilesChanged> changes( final List<ChangedFile> created, final List<ChangedFile> modified,
         final List<ChangedFile> removed ) {
      return Optional.of( new WorkspaceFilesChanged( created, modified, removed ) );
   }
}
