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

package org.eclipse.esmf.ame.services.refactoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

import org.eclipse.esmf.ame.exceptions.RefactoringException;
import org.eclipse.esmf.ame.services.models.refactoring.ChangedFile;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringHistoryEntry;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringRequest;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringType;

import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RefactoringHistoryServiceTest {
   private static final RefactoringRequest REQUEST = new RefactoringRequest( RefactoringType.RENAME_FILE, null, "Renamed.ttl",
         "org.example", "1.0.0", "Modified.ttl", null, null, null, null, null, null );

   @TempDir
   Path tempDir;

   private Path historyPath;
   private RefactoringHistoryService historyService;
   private Path modified;
   private Path removed;
   private Path created;

   @BeforeEach
   void setUp() throws IOException {
      historyPath = tempDir.resolve( "history" );
      historyService = new RefactoringHistoryService( historyPath, JsonMapper.createDefault() );
      final Path directory = Files.createDirectories( tempDir.resolve( "models" ).resolve( "org.example" ).resolve( "1.0.0" ) );
      modified = Files.writeString( directory.resolve( "Modified.ttl" ), "before" );
      removed = Files.writeString( directory.resolve( "Removed.ttl" ), "removed" );
      created = directory.resolve( "Created.ttl" );
   }

   private RefactoringHistoryEntry recordRefactoring() {
      return historyService.record( "Refactoring", REQUEST, List.of( changed( created ) ), List.of( changed( modified ) ),
            List.of( changed( removed ) ), () -> {
               try {
                  Files.writeString( created, "created" );
                  Files.writeString( modified, "after" );
                  Files.delete( removed );
               } catch ( final IOException e ) {
                  throw new UncheckedIOException( e );
               }
            } );
   }

   @Test
   void revertRestoresTheStateBeforeTheRefactoring() throws IOException {
      final RefactoringHistoryEntry entry = recordRefactoring();

      assertEquals( List.of( entry ), new RefactoringHistoryService( historyPath, JsonMapper.createDefault() ).getHistory() );

      historyService.revert( entry.id(), false );

      assertFalse( Files.exists( created ) );
      assertEquals( "before", Files.readString( modified ) );
      assertEquals( "removed", Files.readString( removed ) );
      assertTrue( historyService.getHistory().isEmpty() );
      assertThrows( NoSuchElementException.class, () -> historyService.revert( entry.id(), false ) );
   }

   @Test
   void storesTheRequestOfTheRefactoring() {
      recordRefactoring();

      assertEquals( REQUEST, new RefactoringHistoryService( historyPath, JsonMapper.createDefault() ).getHistory().getFirst().request() );
   }

   @Test
   void loadsRefactoringsRecordedWithoutTheirRequest() throws IOException {
      final RefactoringHistoryEntry entry = recordRefactoring();
      final Path manifest = historyPath.resolve( entry.id() ).resolve( RefactoringHistoryService.MANIFEST );
      Files.writeString( manifest, Files.readString( manifest ).replaceFirst( ",\\s*\"request\"\\s*:\\s*\\{[^}]*}", "" ) );

      final RefactoringHistoryEntry loaded = new RefactoringHistoryService( historyPath, JsonMapper.createDefault() ).getHistory()
            .getFirst();

      assertNull( loaded.request() );
      assertEquals( entry.description(), loaded.description() );
   }

   @Test
   void revertIsRefusedIfARemovedFileWasRecreated() throws IOException {
      final RefactoringHistoryEntry entry = recordRefactoring();
      Files.writeString( removed, "new content" );

      assertThrows( RefactoringException.class, () -> historyService.revert( entry.id(), false ) );
      assertEquals( "new content", Files.readString( removed ) );

      historyService.revert( entry.id(), true );
      assertEquals( "removed", Files.readString( removed ) );
   }

   @Test
   void failedWritesAreRolledBack() throws IOException {
      assertThrows( IllegalStateException.class, () -> historyService.record( "Refactoring", REQUEST,
            List.of( changed( created ) ), List.of( changed( modified ) ), List.of(), () -> {
               try {
                  Files.writeString( created, "created" );
                  Files.writeString( modified, "after" );
               } catch ( final IOException e ) {
                  throw new UncheckedIOException( e );
               }
               throw new IllegalStateException( "disk full" );
            } ) );

      assertFalse( Files.exists( created ) );
      assertEquals( "before", Files.readString( modified ) );
      assertTrue( historyService.getHistory().isEmpty() );
   }

   @Test
   void refusesToOverwriteExistingFiles() throws IOException {
      Files.writeString( created, "existing" );

      assertThrows( RefactoringException.class, this::recordRefactoring );
      assertEquals( "existing", Files.readString( created ) );
      assertEquals( "before", Files.readString( modified ) );
   }

   @Test
   void keepsTheLatestRefactoringsOnly() {
      for ( int i = 0; i < RefactoringHistoryService.MAX_ENTRIES + 2; i++ ) {
         historyService.record( "Refactoring " + i, REQUEST, List.of(), List.of( changed( modified ) ), List.of(), () -> { } );
      }

      final List<RefactoringHistoryEntry> history = historyService.getHistory();
      assertEquals( RefactoringHistoryService.MAX_ENTRIES, history.size() );
      assertEquals( "Refactoring " + ( RefactoringHistoryService.MAX_ENTRIES + 1 ), history.getFirst().description() );
   }

   @Test
   void rejectsIdsOutsideOfTheHistory() {
      assertThrows( IllegalArgumentException.class, () -> historyService.revert( "../models", false ) );
   }

   private static ChangedFile changed( final Path file ) {
      return new ChangedFile( "org.example", "1.0.0", file.getFileName().toString(), file.toString(), false );
   }
}
