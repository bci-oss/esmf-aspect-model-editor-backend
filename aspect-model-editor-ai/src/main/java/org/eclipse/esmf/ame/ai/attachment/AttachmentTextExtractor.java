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
import org.eclipse.esmf.ame.ai.model.attachment.AiAttachment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import io.micronaut.core.annotation.Nullable;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Extracts the text of attached files: PDF (PDFBox), Word and Excel (Office Open XML read with the JDK) and text files.
 */
public final class AttachmentTextExtractor {
   public static final String PDF = "pdf";
   public static final String DOCX = "docx";
   public static final String XLSX = "xlsx";
   public static final String TEXT = "text";

   public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
   public static final int MAX_CHARACTERS = 1_000_000;

   private static final long MAX_ENTRY_SIZE = 50L * 1024 * 1024;
   private static final long MAX_UNPACKED_SIZE = 100L * 1024 * 1024;
   private static final Set<String> TEXT_EXTENSIONS = Set.of( "txt", "md", "markdown", "json", "csv", "tsv", "ttl", "yaml", "yml",
         "xml" );
   private static final String SHEET_PREFIX = "xl/worksheets/";
   private static final String WORKBOOK_RELS = "xl/_rels/workbook.xml.rels";

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
      if ( content.length > MAX_FILE_SIZE ) {
         throw new AiException( AiErrorCode.ATTACHMENT_TOO_LARGE, "The file is larger than 20 MB." );
      }
      final Extracted extracted = switch ( kind ) {
         case PDF -> pdf( content );
         case DOCX -> docx( content );
         case XLSX -> xlsx( content );
         default -> text( content );
      };
      if ( extracted.text().isBlank() && extracted.warning() == null ) {
         throw new AiException( AiErrorCode.ATTACHMENT_EMPTY, "The file contains no text." );
      }
      return extracted;
   }

   private static Extracted text( final byte[] content ) {
      String text = new String( content, StandardCharsets.UTF_8 );
      if ( text.startsWith( "\uFEFF" ) ) {
         text = text.substring( 1 );
      }
      return result( TEXT, text, null, null );
   }

   private static Extracted pdf( final byte[] content ) {
      try ( final PDDocument document = Loader.loadPDF( content ) ) {
         final int pages = document.getNumberOfPages();
         final PDFTextStripper stripper = new PDFTextStripper();
         final StringBuilder text = new StringBuilder();
         boolean hasText = false;
         for ( int page = 1; page <= pages && text.length() <= MAX_CHARACTERS; page++ ) {
            stripper.setStartPage( page );
            stripper.setEndPage( page );
            final String pageText = stripper.getText( document ).strip();
            hasText |= !pageText.isEmpty();
            text.append( "[Page " ).append( page ).append( "]\n" ).append( pageText ).append( "\n\n" );
         }
         if ( !hasText ) {
            return new Extracted( PDF, "", pages, false, pages == 0 ? null : AiAttachment.NO_TEXT );
         }
         return result( PDF, text.toString(), pages, null );
      } catch ( final InvalidPasswordException exception ) {
         throw new AiException( AiErrorCode.ATTACHMENT_UNREADABLE, "The PDF is protected by a password.", exception );
      } catch ( final IOException | RuntimeException exception ) {
         throw unreadable( "PDF", exception );
      }
   }

   private static Extracted docx( final byte[] content ) {
      final Map<String, byte[]> entries = unzip( content, "Word", name -> name.equals( "word/document.xml" ) );
      final byte[] document = entries.get( "word/document.xml" );
      if ( document == null ) {
         throw new AiException( AiErrorCode.ATTACHMENT_UNREADABLE, "The file is not a valid Word document." );
      }
      final StringBuilder text = new StringBuilder();
      // tab stop definitions (w:tabs/w:tab) are no text
      final boolean[] inTabStops = { false };
      parse( document, "Word", reader -> {
         final String name = reader.getLocalName();
         if ( reader.getEventType() == XMLStreamConstants.START_ELEMENT ) {
            switch ( name ) {
               case "t" -> text.append( reader.getElementText() );
               case "tabs" -> inTabStops[0] = true;
               case "tab" -> text.append( inTabStops[0] ? "" : "\t" );
               case "br", "cr" -> text.append( '\n' );
               default -> {
               }
            }
         } else {
            switch ( name ) {
               case "tabs" -> inTabStops[0] = false;
               case "p" -> text.append( '\n' );
               case "tc" -> text.append( '\t' );
               case "tr" -> text.append( '\n' );
               default -> {
               }
            }
         }
      } );
      return result( DOCX, text.toString(), null, null );
   }

   private static Extracted xlsx( final byte[] content ) {
      final Map<String, byte[]> entries = unzip( content, "Excel", name -> name.equals( "xl/sharedStrings.xml" )
            || name.equals( "xl/workbook.xml" ) || name.equals( WORKBOOK_RELS ) || name.startsWith( SHEET_PREFIX ) && name.endsWith( ".xml" ) );
      final byte[] workbook = entries.get( "xl/workbook.xml" );
      if ( workbook == null ) {
         throw new AiException( AiErrorCode.ATTACHMENT_UNREADABLE, "The file is not a valid Excel workbook." );
      }
      final List<String> sharedStrings = sharedStrings( entries.get( "xl/sharedStrings.xml" ) );
      final Map<String, String> targets = relationships( entries.get( WORKBOOK_RELS ) );
      final List<String[]> sheets = new ArrayList<>();
      parse( workbook, "Excel", reader -> {
         if ( reader.getEventType() == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals( "sheet" ) ) {
            final String relationId = attribute( reader, "id" );
            final String target = relationId == null ? null : targets.get( relationId );
            sheets.add( new String[] { attribute( reader, "name" ), target == null ? null : sheetPath( target ) } );
         }
      } );
      final StringBuilder text = new StringBuilder();
      int index = 0;
      for ( final String[] sheet : sheets ) {
         index++;
         final byte[] data = sheet[1] == null ? entries.get( SHEET_PREFIX + "sheet" + index + ".xml" ) : entries.get( sheet[1] );
         if ( data == null ) {
            continue;
         }
         text.append( "[Sheet " ).append( sheet[0] == null ? String.valueOf( index ) : sheet[0] ).append( "]\n" );
         text.append( sheetText( data, sharedStrings ) ).append( '\n' );
      }
      return result( XLSX, text.toString(), sheets.size(), null );
   }

   private static String sheetPath( final String target ) {
      final String path = target.startsWith( "/" ) ? target.substring( 1 ) : "xl/" + target;
      return path.replace( "\\", "/" );
   }

   private static List<String> sharedStrings( final byte[] data ) {
      final List<String> strings = new ArrayList<>();
      if ( data == null ) {
         return strings;
      }
      final StringBuilder current = new StringBuilder();
      parse( data, "Excel", reader -> {
         final boolean start = reader.getEventType() == XMLStreamConstants.START_ELEMENT;
         final String name = reader.getLocalName();
         if ( start && name.equals( "si" ) ) {
            current.setLength( 0 );
         } else if ( start && name.equals( "t" ) ) {
            current.append( reader.getElementText() );
         } else if ( !start && name.equals( "si" ) ) {
            strings.add( current.toString() );
         }
      } );
      return strings;
   }

   private static Map<String, String> relationships( final byte[] data ) {
      final Map<String, String> targets = new HashMap<>();
      if ( data != null ) {
         parse( data, "Excel", reader -> {
            if ( reader.getEventType() == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals( "Relationship" ) ) {
               targets.put( reader.getAttributeValue( null, "Id" ), reader.getAttributeValue( null, "Target" ) );
            }
         } );
      }
      return targets;
   }

   private static String sheetText( final byte[] data, final List<String> sharedStrings ) {
      final StringBuilder text = new StringBuilder();
      final TreeMap<Integer, String> row = new TreeMap<>();
      final String[] cell = new String[2];
      final StringBuilder value = new StringBuilder();
      parse( data, "Excel", reader -> {
         final boolean start = reader.getEventType() == XMLStreamConstants.START_ELEMENT;
         switch ( reader.getLocalName() ) {
            case "row" -> {
               if ( start ) {
                  row.clear();
               } else {
                  appendRow( text, row );
               }
            }
            case "c" -> {
               if ( start ) {
                  cell[0] = attribute( reader, "r" );
                  cell[1] = attribute( reader, "t" );
                  value.setLength( 0 );
               } else if ( !value.isEmpty() ) {
                  final int column = cell[0] == null ? row.size() : column( cell[0] );
                  row.put( column, cellValue( cell[1], value.toString(), sharedStrings ) );
               }
            }
            case "v", "t" -> {
               if ( start ) {
                  value.append( reader.getElementText() );
               }
            }
            default -> {
            }
         }
      } );
      return text.toString();
   }

   private static void appendRow( final StringBuilder text, final TreeMap<Integer, String> row ) {
      if ( row.isEmpty() ) {
         return;
      }
      int column = 0;
      for ( final Map.Entry<Integer, String> entry : row.entrySet() ) {
         while ( column < entry.getKey() ) {
            if ( column > 0 ) {
               text.append( '\t' );
            }
            column++;
         }
         if ( column > 0 ) {
            text.append( '\t' );
         }
         text.append( entry.getValue() );
         column++;
      }
      text.append( '\n' );
   }

   private static String cellValue( final @Nullable String type, final String value, final List<String> sharedStrings ) {
      if ( "s".equals( type ) ) {
         try {
            final int index = Integer.parseInt( value.strip() );
            return index >= 0 && index < sharedStrings.size() ? sharedStrings.get( index ) : "";
         } catch ( final NumberFormatException exception ) {
            return value;
         }
      }
      if ( "b".equals( type ) ) {
         return "1".equals( value.strip() ) ? "TRUE" : "FALSE";
      }
      return value;
   }

   static int column( final String reference ) {
      int column = 0;
      for ( final char character : reference.toCharArray() ) {
         if ( character < 'A' || character > 'Z' ) {
            break;
         }
         column = column * 26 + character - 'A' + 1;
      }
      return Math.max( 0, column - 1 );
   }

   private static @Nullable String attribute( final XMLStreamReader reader, final String localName ) {
      for ( int i = 0; i < reader.getAttributeCount(); i++ ) {
         if ( reader.getAttributeLocalName( i ).equals( localName ) ) {
            return reader.getAttributeValue( i );
         }
      }
      return null;
   }

   private interface ElementHandler {
      void handle( XMLStreamReader reader ) throws XMLStreamException;
   }

   private static void parse( final byte[] data, final String format, final ElementHandler handler ) {
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

   private interface EntryFilter {
      boolean accept( String name );
   }

   private static Map<String, byte[]> unzip( final byte[] content, final String format, final EntryFilter filter ) {
      final Map<String, byte[]> entries = new HashMap<>();
      long total = 0;
      try ( final ZipInputStream zip = new ZipInputStream( new ByteArrayInputStream( content ) ) ) {
         ZipEntry entry;
         while ( ( entry = zip.getNextEntry() ) != null ) {
            if ( entry.isDirectory() || !filter.accept( entry.getName() ) ) {
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

   private static byte[] read( final InputStream input, final long limit ) throws IOException {
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

   private static AiException unreadable( final String format, final Exception exception ) {
      if ( exception instanceof final AiException aiException ) {
         return aiException;
      }
      return new AiException( AiErrorCode.ATTACHMENT_UNREADABLE, "The " + format + " file could not be read.", exception );
   }

   private static Extracted result( final String kind, final String raw, final @Nullable Integer pages, final @Nullable String warning ) {
      final String text = raw.replace( "\r\n", "\n" ).strip();
      final boolean truncated = text.length() > MAX_CHARACTERS;
      return new Extracted( kind, truncated ? text.substring( 0, MAX_CHARACTERS ) : text, pages, truncated, warning );
   }
}
