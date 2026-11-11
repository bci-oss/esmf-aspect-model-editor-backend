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
package org.eclipse.esmf.ame.ai.attachment;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import io.micronaut.core.annotation.Nullable;

/**
 * Reads Office Open XML packages safely: ZIP entries are unpacked with size limits against zip bombs and XML is parsed
 * without DTDs and external entities.
 */
final class OfficePackage {
   static final long MAX_ENTRY_SIZE = 50L * 1024 * 1024;
   static final long MAX_UNPACKED_SIZE = 100L * 1024 * 1024;

   private OfficePackage() {
   }

   /**
    * Handles a start or end element.
    */
   interface ElementHandler {
      void handle( XMLStreamReader reader ) throws XMLStreamException;
   }

   /**
    * Unpacks the accepted entries of a ZIP file.
    *
    * @param content the ZIP file
    * @param format the name of the format for error messages, e.g. {@code Word}
    * @param filter accepts the names of the entries to unpack
    * @return the content of the accepted entries by name
    * @throws AiException if the file is unreadable, contains no accepted entry or its unpacked content is too large
    */
   static Map<String, byte[]> unzip( final byte[] content, final String format, final Predicate<String> filter ) {
      final Map<String, byte[]> entries = new HashMap<>();
      long total = 0;
      try ( final ZipInputStream zip = new ZipInputStream( new ByteArrayInputStream( content ) ) ) {
         ZipEntry entry;
         while ( ( entry = zip.getNextEntry() ) != null ) {
            if ( entry.isDirectory() || !filter.test( entry.getName() ) ) {
               continue;
            }
            final byte[] data = read( zip, Math.min( MAX_ENTRY_SIZE, MAX_UNPACKED_SIZE - total ) );
            total += data.length;
            entries.put( entry.getName(), data );
         }
      } catch ( final IOException | RuntimeException exception ) {
         throw unreadable( format, exception );
      }
      if ( entries.isEmpty() ) {
         throw new AiException( AiErrorCode.ATTACHMENT_UNREADABLE, "The file is not a valid " + format + " document." );
      }
      return entries;
   }

   /**
    * Reads a stream up to a limit.
    *
    * @throws AiException with {@link AiErrorCode#ATTACHMENT_TOO_LARGE} if the stream is longer than the limit
    */
   static byte[] read( final InputStream input, final long limit ) throws IOException {
      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      final byte[] buffer = new byte[8192];
      long size = 0;
      int read;
      while ( ( read = input.read( buffer ) ) != -1 ) {
         size += read;
         if ( size > limit ) {
            throw new AiException( AiErrorCode.ATTACHMENT_TOO_LARGE, "The unpacked content of the file is too large." );
         }
         output.write( buffer, 0, read );
      }
      return output.toByteArray();
   }

   /**
    * Parses XML and passes every start and end element to the handler.
    *
    * @param format the name of the format for error messages
    */
   static void parse( final byte[] data, final String format, final ElementHandler handler ) {
      final XMLInputFactory factory = XMLInputFactory.newFactory();
      factory.setProperty( XMLInputFactory.SUPPORT_DTD, false );
      factory.setProperty( XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false );
      try {
         final XMLStreamReader reader = factory.createXMLStreamReader( new ByteArrayInputStream( data ) );
         try {
            while ( reader.hasNext() ) {
               final int event = reader.next();
               if ( event == XMLStreamConstants.START_ELEMENT || event == XMLStreamConstants.END_ELEMENT ) {
                  handler.handle( reader );
               }
            }
         } finally {
            reader.close();
         }
      } catch ( final XMLStreamException | RuntimeException exception ) {
         throw unreadable( format, exception );
      }
   }

   /**
    * @return the value of the attribute with the local name, ignoring its namespace, or {@code null}
    */
   static @Nullable String attribute( final XMLStreamReader reader, final String localName ) {
      for ( int i = 0; i < reader.getAttributeCount(); i++ ) {
         if ( reader.getAttributeLocalName( i ).equals( localName ) ) {
            return reader.getAttributeValue( i );
         }
      }
      return null;
   }

   /**
    * @return the exception itself if it is an {@link AiException}, otherwise an {@link AiErrorCode#ATTACHMENT_UNREADABLE} error
    */
   static AiException unreadable( final String format, final Exception exception ) {
      if ( exception instanceof final AiException aiException ) {
         return aiException;
      }
      return new AiException( AiErrorCode.ATTACHMENT_UNREADABLE, "The " + format + " file could not be read.", exception );
   }
}
