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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.esmf.ame.services.models.refactoring.ChangedFile;
import org.eclipse.esmf.ame.services.models.workspace.WorkspaceFilesChanged;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService.WorkspaceFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects which model files of a set of workspace roots were created, modified or removed since the previous scan.
 * <p>
 * Every scan lists the files and compares them with the fingerprints of the previous scan. The content hash is only
 * computed if the size or the modification time changed, so a scan of an unchanged workspace only reads file
 * attributes. A file whose timestamp changed but whose content is identical (e.g. saving without edits) is not
 * reported. The class is not thread-safe; callers must serialize the scans.
 */
class WorkspaceChangeDetector {
   private static final Logger LOG = LoggerFactory.getLogger( WorkspaceChangeDetector.class );

   private final Supplier<List<Path>> roots;
   private final Function<Path, List<WorkspaceFile>> lister;
   private Map<Path, Fingerprint> index = Map.of();

   /**
    * @param roots supplies the workspace roots; read on every scan, so added or removed workspaces are picked up
    * @param lister lists the model files of a root
    */
   WorkspaceChangeDetector( final Supplier<List<Path>> roots, final Function<Path, List<WorkspaceFile>> lister ) {
      this.roots = roots;
      this.lister = lister;
   }

   /**
    * Scans the roots and remembers their current state.
    *
    * @return the changes since the previous scan; empty if the roots could not be read completely, in which case the
    *       previous state is kept, so that unreadable files are not reported as removed and created again
    */
   Optional<WorkspaceFilesChanged> scan() {
      final Map<Path, Fingerprint> current;
      try {
         current = fingerprints();
      } catch ( final UncheckedIOException e ) {
         LOG.debug( "Skipping the workspace scan: {}", e.getMessage() );
         return Optional.empty();
      }
      final List<ChangedFile> created = new ArrayList<>();
      final List<ChangedFile> modified = new ArrayList<>();
      final List<ChangedFile> removed = new ArrayList<>();
      current.forEach( ( path, fingerprint ) -> {
         final Fingerprint previous = index.get( path );
         if ( previous == null ) {
            created.add( fingerprint.toChangedFile() );
         } else if ( !previous.hash().equals( fingerprint.hash() ) ) {
            modified.add( fingerprint.toChangedFile() );
         }
      } );
      index.forEach( ( path, fingerprint ) -> {
         if ( !current.containsKey( path ) ) {
            removed.add( fingerprint.toChangedFile() );
         }
      } );
      index = current;
      return Optional.of( new WorkspaceFilesChanged( created, modified, removed ) );
   }

   private Map<Path, Fingerprint> fingerprints() {
      final Map<Path, Fingerprint> result = new LinkedHashMap<>();
      for ( final Path root : roots.get() ) {
         for ( final WorkspaceFile file : lister.apply( root ) ) {
            final Path path = file.path().toAbsolutePath().normalize();
            if ( !result.containsKey( path ) ) {
               fingerprint( path, file, index.get( path ) ).ifPresent( fingerprint -> result.put( path, fingerprint ) );
            }
         }
      }
      return result;
   }

   /**
    * @return the fingerprint, empty if the file disappeared in the meantime
    */
   private static Optional<Fingerprint> fingerprint( final Path path, final WorkspaceFile file, final Fingerprint previous ) {
      try {
         final BasicFileAttributes attributes = Files.readAttributes( path, BasicFileAttributes.class );
         if ( previous != null && previous.size() == attributes.size() && previous.modified().equals( attributes.lastModifiedTime() ) ) {
            return Optional.of( previous );
         }
         return Optional.of( new Fingerprint( path, file, attributes.size(), attributes.lastModifiedTime(), hash( path ) ) );
      } catch ( final NoSuchFileException e ) {
         return Optional.empty();
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not read " + path, e );
      }
   }

   private static String hash( final Path file ) throws IOException {
      final MessageDigest digest;
      try {
         digest = MessageDigest.getInstance( "SHA-256" );
      } catch ( final NoSuchAlgorithmException e ) {
         throw new IllegalStateException( "Every Java platform supports SHA-256", e );
      }
      try ( final InputStream input = new DigestInputStream( Files.newInputStream( file ), digest ) ) {
         input.transferTo( OutputStream.nullOutputStream() );
      }
      return HexFormat.of().formatHex( digest.digest() );
   }

   private record Fingerprint( Path path, WorkspaceFile file, long size, FileTime modified, String hash ) {
      ChangedFile toChangedFile() {
         return new ChangedFile( file.namespace(), file.version(), file.fileName(), path.toString(), false );
      }
   }
}
