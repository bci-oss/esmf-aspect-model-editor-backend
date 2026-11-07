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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.config.ApplicationSettings;
import org.eclipse.esmf.ame.exceptions.RefactoringException;
import org.eclipse.esmf.ame.services.models.refactoring.ChangedFile;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringHistoryEntry;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringSnapshot;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringSnapshot.Change;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringSnapshot.FileState;

import io.micronaut.json.JsonMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps backups of the files changed by the latest refactorings, so that they can be reverted, also after a restart.
 * <p>
 * Each refactoring is stored in its own directory containing a {@value #MANIFEST} and a copy of each file it modified
 * or removed. A revert is refused if an affected file was changed after the refactoring, unless it is forced.
 * </p>
 */
@Singleton
public class RefactoringHistoryService {
   static final int MAX_ENTRIES = 10;
   static final String MANIFEST = "manifest.json";

   private static final Logger LOG = LoggerFactory.getLogger( RefactoringHistoryService.class );

   private final Path historyPath;
   private final JsonMapper jsonMapper;

   @Inject
   public RefactoringHistoryService( final JsonMapper jsonMapper ) {
      this( ApplicationSettings.getRefactoringHistoryPath(), jsonMapper );
   }

   public RefactoringHistoryService( final Path historyPath, final JsonMapper jsonMapper ) {
      this.historyPath = historyPath;
      this.jsonMapper = jsonMapper;
   }

   /**
    * Backs up the files a refactoring is going to modify or remove, writes the refactoring and records it. If writing
    * fails, the backed up files are restored and the files created so far are removed.
    *
    * @param description a short description of the refactoring
    * @param created the files the refactoring creates
    * @param modified the files the refactoring modifies
    * @param removed the files the refactoring removes
    * @param write writes the refactoring to disk
    * @return the recorded refactoring
    * @throws RefactoringException if a file to create exists already
    */
   public synchronized RefactoringHistoryEntry record( final String description, final List<ChangedFile> created,
         final List<ChangedFile> modified, final List<ChangedFile> removed, final Runnable write ) {
      requireNotExisting( created );
      final String id = UUID.randomUUID().toString();
      final Path entryDirectory = historyPath.resolve( id );
      final List<FileState> backups = new ArrayList<>();
      try {
         Files.createDirectories( entryDirectory );
         backup( entryDirectory, modified, Change.MODIFIED, backups );
         backup( entryDirectory, removed, Change.REMOVED, backups );
      } catch ( final IOException e ) {
         deleteQuietly( entryDirectory );
         throw new UncheckedIOException( "Could not back up the files to refactor", e );
      }

      try {
         write.run();
      } catch ( final RuntimeException e ) {
         LOG.warn( "Writing the refactoring '{}' failed, restoring the previous state", description, e );
         restore( entryDirectory, withCreated( backups, created ) );
         deleteQuietly( entryDirectory );
         throw e;
      }

      final RefactoringHistoryEntry entry = new RefactoringHistoryEntry( id, description, System.currentTimeMillis(), created, modified,
            removed );
      final List<FileState> files = new ArrayList<>( created.stream()
            .map( file -> new FileState( file.path(), Change.CREATED, null, hash( Path.of( file.path() ) ) ) )
            .toList() );
      backups.forEach( state -> files.add( state.change() == Change.MODIFIED
            ? new FileState( state.path(), state.change(), state.backup(), hash( Path.of( state.path() ) ) )
            : state ) );
      writeManifest( entryDirectory, new RefactoringSnapshot( entry, files ) );
      prune();
      return entry;
   }

   /**
    * Lists the recorded refactorings, the latest first.
    *
    * @return the refactorings that can be reverted
    */
   public synchronized List<RefactoringHistoryEntry> getHistory() {
      return snapshots().stream().map( RefactoringSnapshot::entry ).toList();
   }

   /**
    * Restores the state before a refactoring and removes it from the history.
    *
    * @param id the id of the refactoring
    * @param force whether to revert even if affected files were changed after the refactoring
    * @return the reverted refactoring
    * @throws java.util.NoSuchElementException if there is no such refactoring
    * @throws RefactoringException if affected files were changed after the refactoring and {@code force} is not set
    */
   public synchronized RefactoringHistoryEntry revert( final String id, final boolean force ) {
      final Path entryDirectory = entryDirectory( id );
      final RefactoringSnapshot snapshot = readManifest( entryDirectory )
            .orElseThrow( () -> new NoSuchElementException( "The refactoring cannot be reverted anymore: " + id ) );

      final List<String> conflicts = snapshot.files().stream().filter( RefactoringHistoryService::isChangedSince ).map( FileState::path )
            .toList();
      if ( !conflicts.isEmpty() && !force ) {
         throw new RefactoringException( "These files were changed after the refactoring: " + String.join( ", ", conflicts ) );
      }

      restore( entryDirectory, snapshot.files() );
      deleteQuietly( entryDirectory );
      LOG.info( "Reverted refactoring '{}'", snapshot.entry().description() );
      return snapshot.entry();
   }

   /**
    * Removes the oldest refactorings beyond {@value #MAX_ENTRIES} and leftovers of interrupted refactorings.
    */
   public synchronized void prune() {
      final List<Path> recorded = snapshots().stream().map( snapshot -> historyPath.resolve( snapshot.entry().id() ) ).toList();
      recorded.stream().skip( MAX_ENTRIES ).forEach( RefactoringHistoryService::deleteQuietly );
      directories().filter( directory -> !recorded.contains( directory ) ).forEach( RefactoringHistoryService::deleteQuietly );
   }

   private Path entryDirectory( final String id ) {
      final Path directory = historyPath.resolve( id ).normalize();
      if ( !Objects.equals( directory.getParent(), historyPath.normalize() ) ) {
         throw new IllegalArgumentException( "Invalid refactoring id: " + id );
      }
      return directory;
   }

   private static void requireNotExisting( final List<ChangedFile> created ) {
      final List<String> existing = created.stream().map( ChangedFile::path ).filter( path -> Files.exists( Path.of( path ) ) ).toList();
      if ( !existing.isEmpty() ) {
         throw new RefactoringException( "These files exist already: " + String.join( ", ", existing ) );
      }
   }

   private static void backup( final Path entryDirectory, final List<ChangedFile> files, final Change change,
         final List<FileState> backups ) throws IOException {
      for ( final ChangedFile file : files ) {
         final String backupName = backups.size() + ".ttl";
         Files.copy( Path.of( file.path() ), entryDirectory.resolve( backupName ) );
         backups.add( new FileState( file.path(), change, backupName, null ) );
      }
   }

   private static List<FileState> withCreated( final List<FileState> backups, final List<ChangedFile> created ) {
      final List<FileState> files = new ArrayList<>( backups );
      created.forEach( file -> files.add( new FileState( file.path(), Change.CREATED, null, null ) ) );
      return files;
   }

   private static boolean isChangedSince( final FileState file ) {
      final Path path = Path.of( file.path() );
      return switch ( file.change() ) {
         case CREATED, MODIFIED -> !Files.isRegularFile( path ) || !Objects.equals( hash( path ), file.hashAfter() );
         case REMOVED -> Files.exists( path );
      };
   }

   private static void restore( final Path entryDirectory, final List<FileState> files ) {
      files.stream().filter( file -> file.change() == Change.CREATED ).forEach( file -> {
         final Path path = Path.of( file.path() );
         try {
            Files.deleteIfExists( path );
            deleteEmptyParents( path );
         } catch ( final IOException e ) {
            throw new UncheckedIOException( "Could not remove " + path, e );
         }
      } );
      files.stream().filter( file -> file.change() != Change.CREATED ).forEach( file -> {
         final Path path = Path.of( file.path() );
         try {
            Files.createDirectories( path.getParent() );
            Files.copy( entryDirectory.resolve( Objects.requireNonNull( file.backup() ) ), path,
                  StandardCopyOption.REPLACE_EXISTING );
         } catch ( final IOException e ) {
            throw new UncheckedIOException( "Could not restore " + path, e );
         }
      } );
   }

   /**
    * Removes the version and namespace directories of a removed file if they are empty now.
    */
   static void deleteEmptyParents( final Path file ) throws IOException {
      Path directory = file.getParent();
      for ( int level = 0; level < 2 && directory != null && isEmptyDirectory( directory ); level++ ) {
         Files.delete( directory );
         directory = directory.getParent();
      }
   }

   private static boolean isEmptyDirectory( final Path directory ) throws IOException {
      if ( !Files.isDirectory( directory ) ) {
         return false;
      }
      try ( final DirectoryStream<Path> entries = Files.newDirectoryStream( directory ) ) {
         return !entries.iterator().hasNext();
      }
   }

   private List<RefactoringSnapshot> snapshots() {
      return directories()
            .map( this::readManifest )
            .flatMap( Optional::stream )
            .sorted( Comparator.comparingLong( ( RefactoringSnapshot snapshot ) -> snapshot.entry().timestamp() ).reversed() )
            .toList();
   }

   private Stream<Path> directories() {
      if ( !Files.isDirectory( historyPath ) ) {
         return Stream.empty();
      }
      try ( final Stream<Path> entries = Files.list( historyPath ) ) {
         return entries.filter( Files::isDirectory ).toList().stream();
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not read the refactoring history", e );
      }
   }

   private Optional<RefactoringSnapshot> readManifest( final Path entryDirectory ) {
      final Path manifest = entryDirectory.resolve( MANIFEST );
      if ( !Files.isRegularFile( manifest ) ) {
         return Optional.empty();
      }
      try {
         return Optional.ofNullable( jsonMapper.readValue( Files.readString( manifest, StandardCharsets.UTF_8 ), RefactoringSnapshot.class ) );
      } catch ( final IOException | RuntimeException e ) {
         LOG.warn( "Ignoring unreadable refactoring history entry {}: {}", entryDirectory, e.getMessage() );
         return Optional.empty();
      }
   }

   private void writeManifest( final Path entryDirectory, final RefactoringSnapshot snapshot ) {
      try {
         Files.writeString( entryDirectory.resolve( MANIFEST ), jsonMapper.writeValueAsString( snapshot ), StandardCharsets.UTF_8 );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not record the refactoring", e );
      }
   }

   static String hash( final Path file ) {
      try {
         return HexFormat.of().formatHex( MessageDigest.getInstance( "SHA-256" ).digest( Files.readAllBytes( file ) ) );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not read " + file, e );
      } catch ( final NoSuchAlgorithmException e ) {
         throw new IllegalStateException( e );
      }
   }

   private static void deleteQuietly( final Path directory ) {
      FileUtils.deleteQuietly( directory.toFile() );
   }
}
