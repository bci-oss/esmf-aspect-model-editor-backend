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

package org.eclipse.esmf.ame.services.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import org.eclipse.esmf.ame.exceptions.InvalidPluginException;

/**
 * Extracts plugin archives. Entries leaving the target directory are rejected and the size of the extracted content is
 * limited, so that a broken or malicious archive cannot write elsewhere or fill the disk.
 */
final class PluginArchive {
   static final int MAX_ENTRIES = 10_000;
   static final long MAX_EXTRACTED_BYTES = 512L * 1024 * 1024;

   private PluginArchive() {
   }

   /**
    * Extracts a ZIP archive.
    *
    * @param archive the content of the archive
    * @param target the empty directory to extract to
    * @throws InvalidPluginException if the archive is not a ZIP file, contains unsafe paths or exceeds the limits
    * @throws IOException if writing the files fails
    */
   static void extract( final InputStream archive, final Path target ) throws IOException {
      final Path root = target.toAbsolutePath().normalize();
      int entries = 0;
      long extractedBytes = 0;
      try ( final ZipInputStream zip = new ZipInputStream( archive ) ) {
         for ( ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry() ) {
            if ( ++entries > MAX_ENTRIES ) {
               throw new InvalidPluginException( "The plugin archive contains more than " + MAX_ENTRIES + " entries." );
            }
            final Path destination = resolve( root, entry.getName() );
            if ( entry.isDirectory() ) {
               Files.createDirectories( destination );
               continue;
            }
            Files.createDirectories( destination.getParent() );
            extractedBytes = copy( zip, destination, extractedBytes );
         }
      } catch ( final ZipException e ) {
         throw new InvalidPluginException( "The plugin file is not a valid ZIP archive: " + e.getMessage(), e );
      }
      if ( entries == 0 ) {
         throw new InvalidPluginException( "The plugin file is not a ZIP archive or is empty." );
      }
   }

   private static Path resolve( final Path root, final String name ) {
      final Path destination = root.resolve( name.replace( '\\', '/' ) ).normalize();
      if ( !destination.startsWith( root ) || destination.equals( root ) ) {
         throw new InvalidPluginException( "The plugin archive contains the unsafe path '" + name + "'." );
      }
      return destination;
   }

   private static long copy( final InputStream entry, final Path destination, final long extractedBefore ) throws IOException {
      final byte[] buffer = new byte[8192];
      long extracted = extractedBefore;
      try ( final OutputStream out = Files.newOutputStream( destination ) ) {
         for ( int read = entry.read( buffer ); read != -1; read = entry.read( buffer ) ) {
            extracted += read;
            if ( extracted > MAX_EXTRACTED_BYTES ) {
               throw new InvalidPluginException( "The extracted plugin exceeds " + MAX_EXTRACTED_BYTES / ( 1024 * 1024 ) + " MB." );
            }
            out.write( buffer, 0, read );
         }
      }
      return extracted;
   }
}
