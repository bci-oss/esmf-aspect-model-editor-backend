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

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reads the text of a PDF page by page with PDFBox. A PDF without any text, e.g. a scan, yields an empty text with the
 * warning {@link AiAttachment#NO_TEXT}.
 */
final class PdfTextExtractor implements FormatExtractor {
   @Override
   public AttachmentTextExtractor.Extracted extract( final byte[] content ) {
      try ( final PDDocument document = Loader.loadPDF( content ) ) {
         final int pages = document.getNumberOfPages();
         final PDFTextStripper stripper = new PDFTextStripper();
         final StringBuilder text = new StringBuilder();
         boolean hasText = false;
         for ( int page = 1; page <= pages && text.length() <= AttachmentTextExtractor.MAX_CHARACTERS; page++ ) {
            stripper.setStartPage( page );
            stripper.setEndPage( page );
            final String pageText = stripper.getText( document ).strip();
            hasText |= !pageText.isEmpty();
            text.append( "[Page " ).append( page ).append( "]\n" ).append( pageText ).append( "\n\n" );
         }
         if ( !hasText ) {
            return new AttachmentTextExtractor.Extracted( AttachmentTextExtractor.PDF, "", pages, false,
                  pages == 0 ? null : AiAttachment.NO_TEXT );
         }
         return AttachmentTextExtractor.result( AttachmentTextExtractor.PDF, text.toString(), pages, null );
      } catch ( final InvalidPasswordException exception ) {
         throw new AiException( AiErrorCode.ATTACHMENT_UNREADABLE, "The PDF is protected by a password.", exception );
      } catch ( final IOException | RuntimeException exception ) {
         throw OfficePackage.unreadable( "PDF", exception );
      }
   }
}
