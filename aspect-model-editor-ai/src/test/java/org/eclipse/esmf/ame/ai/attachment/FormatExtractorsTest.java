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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;

import org.junit.jupiter.api.Test;

/**
 * Tests the format extractors directly, without the checks of {@link AttachmentTextExtractor#extract}.
 */
class FormatExtractorsTest {
   private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

   @Test
   void readsTextWithoutByteOrderMarkAndNormalizesLineBreaks() {
      final AttachmentTextExtractor.Extracted extracted = new PlainTextExtractor()
            .extract( "\uFEFF a\r\nb \n".getBytes( StandardCharsets.UTF_8 ) );
      assertEquals( AttachmentTextExtractor.TEXT, extracted.kind() );
      assertEquals( "a\nb", extracted.text() );
      assertFalse( extracted.truncated() );
   }

   @Test
   void readsPdfPages() throws IOException {
      final AttachmentTextExtractor.Extracted extracted = new PdfTextExtractor().extract( AttachmentTextExtractorTest.pdf( "One", "Two" ) );
      assertEquals( AttachmentTextExtractor.PDF, extracted.kind() );
      assertEquals( 2, extracted.pages() );
      assertTrue( extracted.text().startsWith( "[Page 1]\nOne" ) );
   }

   @Test
   void returnsAnEmptyTextWithoutWarningForPdfsWithoutPages() throws IOException {
      final AttachmentTextExtractor.Extracted extracted = new PdfTextExtractor().extract( AttachmentTextExtractorTest.pdf() );
      assertEquals( "", extracted.text() );
      assertEquals( 0, extracted.pages() );
      assertNull( extracted.warning() );
   }

   @Test
   void rejectsBrokenPdfs() {
      final AiException exception = assertThrows( AiException.class,
            () -> new PdfTextExtractor().extract( "no pdf".getBytes( StandardCharsets.UTF_8 ) ) );
      assertEquals( AiErrorCode.ATTACHMENT_UNREADABLE, exception.getCode() );
      assertEquals( "The PDF file could not be read.", exception.getMessage() );
   }

   @Test
   void ignoresTabStopDefinitionsInWordDocuments() throws IOException {
      final byte[] docx = AttachmentTextExtractorTest.zip( Map.of( "word/document.xml", "<w:document xmlns:w=\"" + W + "\"><w:body>"
            + "<w:p><w:pPr><w:tabs><w:tab w:val=\"left\"/></w:tabs></w:pPr><w:r><w:t>a</w:t><w:tab/><w:t>b</w:t></w:r></w:p>"
            + "</w:body></w:document>" ) );
      assertEquals( "a\tb", new DocxTextExtractor().extract( docx ).text() );
   }

   @Test
   void rejectsWordFilesWithoutDocument() throws IOException {
      final AiException exception = assertThrows( AiException.class,
            () -> new DocxTextExtractor().extract( AttachmentTextExtractorTest.zip( Map.of( "other.xml", "<a/>" ) ) ) );
      assertEquals( "The file is not a valid Word document.", exception.getMessage() );
   }
}
