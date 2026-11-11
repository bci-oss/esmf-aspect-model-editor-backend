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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;

/**
 * Reads the text of an Excel workbook: every sheet in workbook order as {@code [Sheet name]} followed by its rows, cells
 * separated by tabs. The sheet files are resolved via the workbook relationships.
 */
final class XlsxTextExtractor implements FormatExtractor {
   static final String FORMAT = "Excel";
   private static final String SHEET_PREFIX = "xl/worksheets/";
   private static final String WORKBOOK = "xl/workbook.xml";
   private static final String WORKBOOK_RELS = "xl/_rels/workbook.xml.rels";
   private static final String SHARED_STRINGS = "xl/sharedStrings.xml";

   private record Sheet( String name, String path ) {
   }

   @Override
   public AttachmentTextExtractor.Extracted extract( final byte[] content ) {
      final Map<String, byte[]> entries = OfficePackage.unzip( content, FORMAT, name -> name.equals( SHARED_STRINGS )
            || name.equals( WORKBOOK ) || name.equals( WORKBOOK_RELS ) || name.startsWith( SHEET_PREFIX ) && name.endsWith( ".xml" ) );
      final byte[] workbook = entries.get( WORKBOOK );
      if ( workbook == null ) {
         throw new AiException( AiErrorCode.ATTACHMENT_UNREADABLE, "The file is not a valid Excel workbook." );
      }
      final List<String> sharedStrings = sharedStrings( entries.get( SHARED_STRINGS ) );
      final List<Sheet> sheets = sheets( workbook, relationships( entries.get( WORKBOOK_RELS ) ) );
      final StringBuilder text = new StringBuilder();
      int index = 0;
      for ( final Sheet sheet : sheets ) {
         index++;
         final byte[] data = sheet.path() == null ? entries.get( SHEET_PREFIX + "sheet" + index + ".xml" ) : entries.get( sheet.path() );
         if ( data == null ) {
            continue;
         }
         text.append( "[Sheet " ).append( sheet.name() == null ? String.valueOf( index ) : sheet.name() ).append( "]\n" );
         text.append( XlsxSheetReader.text( data, sharedStrings ) ).append( '\n' );
      }
      return AttachmentTextExtractor.result( AttachmentTextExtractor.XLSX, text.toString(), sheets.size(), null );
   }

   private static List<Sheet> sheets( final byte[] workbook, final Map<String, String> targets ) {
      final List<Sheet> sheets = new ArrayList<>();
      OfficePackage.parse( workbook, FORMAT, reader -> {
         if ( reader.getEventType() == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals( "sheet" ) ) {
            final String relationId = OfficePackage.attribute( reader, "id" );
            final String target = relationId == null ? null : targets.get( relationId );
            sheets.add( new Sheet( OfficePackage.attribute( reader, "name" ), target == null ? null : sheetPath( target ) ) );
         }
      } );
      return sheets;
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
      OfficePackage.parse( data, FORMAT, reader -> {
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
         OfficePackage.parse( data, FORMAT, reader -> {
            if ( reader.getEventType() == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals( "Relationship" ) ) {
               targets.put( reader.getAttributeValue( null, "Id" ), reader.getAttributeValue( null, "Target" ) );
            }
         } );
      }
      return targets;
   }
}
