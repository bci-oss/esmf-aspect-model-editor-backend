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

import java.util.Map;
import javax.xml.stream.XMLStreamConstants;

/**
 * Reads the text of a Word document ({@code word/document.xml}): paragraphs and table rows become lines, tabs and table
 * cells become tab characters.
 */
final class DocxTextExtractor implements FormatExtractor {
   private static final String FORMAT = "Word";
   private static final String DOCUMENT = "word/document.xml";

   @Override
   public AttachmentTextExtractor.Extracted extract( final byte[] content ) {
      final Map<String, byte[]> entries = OfficePackage.unzip( content, FORMAT, DOCUMENT::equals );
      final byte[] document = entries.get( DOCUMENT );
      if ( document == null ) {
         throw new AiException( AiErrorCode.ATTACHMENT_UNREADABLE, "The file is not a valid Word document." );
      }
      final StringBuilder text = new StringBuilder();
      // tab stop definitions (w:tabs/w:tab) are no text
      final boolean[] inTabStops = { false };
      OfficePackage.parse( document, FORMAT, reader -> {
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
      return AttachmentTextExtractor.result( AttachmentTextExtractor.DOCX, text.toString(), null, null );
   }
}
