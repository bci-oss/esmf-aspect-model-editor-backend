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

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.micronaut.core.annotation.Nullable;

/**
 * Extracts the text of attached files: PDF (PDFBox), Word and Excel (Office Open XML read with the JDK) and text files. The
 * format is chosen by the file extension; the work is done by one {@link FormatExtractor} per format.
 */
public final class AttachmentTextExtractor {
   public static final String PDF = "pdf";
   public static final String DOCX = "docx";
   public static final String XLSX = "xlsx";
   public static final String TEXT = "text";

   public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
   public static final int MAX_CHARACTERS = 1_000_000;

   /**
    * The message of the error for files larger than {@link #MAX_FILE_SIZE}.
    */
   public static final String TOO_LARGE_MESSAGE = "The file is larger than " + MAX_FILE_SIZE / ( 1024 * 1024 ) + " MB.";

   private static final Set<String> TEXT_EXTENSIONS = Set.of( "txt", "md", "markdown", "json", "csv", "tsv", "ttl", "yaml", "yml",
         "xml" );
   private static final Map<String, FormatExtractor> EXTRACTORS = Map.of(
         PDF, new PdfTextExtractor(),
         DOCX, new DocxTextExtractor(),
         XLSX, new XlsxTextExtractor(),
         TEXT, new PlainTextExtractor() );

   private AttachmentTextExtractor() {
   }

   /**
    * The extracted text of a file.
    *
    * @param kind {@link #PDF}, {@link #DOCX}, {@link #XLSX} or {@link #TEXT}
    * @param text the text, cut at {@link #MAX_CHARACTERS}
    * @param pages the pages of a PDF or the sheets of a workbook
    * @param truncated whether the text was cut
    * @param warning a hint such as {@code no_text}
    */
   public record Extracted( String kind, String text, @Nullable Integer pages, boolean truncated, @Nullable String warning ) {
   }

   /**
    * @param fileName the name of the file
    * @return the kind of the file or {@code null} if the file is not supported
    */
   public static @Nullable String kind( final @Nullable String fileName ) {
      if ( fileName == null ) {
         return null;
      }
      final int dot = fileName.lastIndexOf( '.' );
      final String extension = dot < 0 ? "" : fileName.substring( dot + 1 ).toLowerCase( Locale.ROOT );
      return switch ( extension ) {
         case PDF -> PDF;
         case DOCX -> DOCX;
         case XLSX -> XLSX;
         default -> TEXT_EXTENSIONS.contains( extension ) ? TEXT : null;
      };
   }

   /**
    * Checks the size of a file before it is read.
    *
    * @param size the size of the file in bytes
    * @throws AiException with {@link AiErrorCode#ATTACHMENT_TOO_LARGE} if the file is larger than {@link #MAX_FILE_SIZE}
    */
   public static void checkFileSize( final long size ) {
      if ( size > MAX_FILE_SIZE ) {
         throw new AiException( AiErrorCode.ATTACHMENT_TOO_LARGE, TOO_LARGE_MESSAGE );
      }
   }

   /**
    * Extracts the text of a file.
    *
    * @param fileName the name of the file, its extension decides the format
    * @param content the content of the file
    * @return the extracted text
    * @throws AiException if the file is not supported, too large, unreadable or contains no text
    */
   public static Extracted extract( final String fileName, final byte[] content ) {
      final String kind = kind( fileName );
      if ( kind == null ) {
         throw new AiException( AiErrorCode.ATTACHMENT_UNSUPPORTED,
               "Only PDF, Word (.docx), Excel (.xlsx) and text files (" + String.join( ", ", TEXT_EXTENSIONS.stream().sorted().toList() )
                     + ") can be attached." );
      }
      checkFileSize( content.length );
      final Extracted extracted = EXTRACTORS.get( kind ).extract( content );
      if ( extracted.text().isBlank() && extracted.warning() == null ) {
         throw new AiException( AiErrorCode.ATTACHMENT_EMPTY, "The file contains no text." );
      }
      return extracted;
   }

   /**
    * Normalizes line breaks, strips the text and cuts it at {@link #MAX_CHARACTERS}.
    */
   static Extracted result( final String kind, final String raw, final @Nullable Integer pages, final @Nullable String warning ) {
      final String text = raw.replace( "\r\n", "\n" ).strip();
      final boolean truncated = text.length() > MAX_CHARACTERS;
      return new Extracted( kind, truncated ? text.substring( 0, MAX_CHARACTERS ) : text, pages, truncated, warning );
   }
}
