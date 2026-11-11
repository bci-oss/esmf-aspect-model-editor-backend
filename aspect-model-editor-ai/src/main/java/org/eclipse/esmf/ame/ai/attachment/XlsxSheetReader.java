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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.stream.XMLStreamConstants;

import io.micronaut.core.annotation.Nullable;

/**
 * Reads the rows of one worksheet of an Excel workbook as tab separated lines; gaps between cells are kept as empty columns.
 */
final class XlsxSheetReader {
   private XlsxSheetReader() {
   }

   /**
    * @param data the XML of the worksheet
    * @param sharedStrings the shared strings of the workbook
    * @return one line per non-empty row
    */
   static String text( final byte[] data, final List<String> sharedStrings ) {
      final StringBuilder text = new StringBuilder();
      final TreeMap<Integer, String> row = new TreeMap<>();
      final String[] cell = new String[2];
      final StringBuilder value = new StringBuilder();
      OfficePackage.parse( data, XlsxTextExtractor.FORMAT, reader -> {
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
                  cell[0] = OfficePackage.attribute( reader, "r" );
                  cell[1] = OfficePackage.attribute( reader, "t" );
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

   static String cellValue( final @Nullable String type, final String value, final List<String> sharedStrings ) {
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

   /**
    * @param reference a cell reference such as {@code AA10}
    * @return the zero based column index
    */
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
}
