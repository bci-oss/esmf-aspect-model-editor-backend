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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.model.attachment.AiAttachment;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class AttachmentTextExtractorTest {
   private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
   private static final String S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
   private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

   private static AiErrorCode failure( final String fileName, final byte[] content ) {
      return assertThrows( AiException.class, () -> AttachmentTextExtractor.extract( fileName, content ) ).getCode();
   }

   private static byte[] utf8( final String text ) {
      return text.getBytes( StandardCharsets.UTF_8 );
   }

   static byte[] pdf( final String... pages ) throws IOException {
      try ( final PDDocument document = new PDDocument(); final ByteArrayOutputStream output = new ByteArrayOutputStream() ) {
         for ( final String text : pages ) {
            final PDPage page = new PDPage();
            document.addPage( page );
            if ( text != null ) {
               try ( final PDPageContentStream stream = new PDPageContentStream( document, page ) ) {
                  stream.beginText();
                  stream.setFont( new PDType1Font( Standard14Fonts.FontName.HELVETICA ), 12 );
                  stream.newLineAtOffset( 50, 700 );
                  stream.showText( text );
                  stream.endText();
               }
            }
         }
         document.save( output );
         return output.toByteArray();
      }
   }

   static byte[] zip( final Map<String, String> entries ) throws IOException {
      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      try ( final ZipOutputStream zip = new ZipOutputStream( output ) ) {
         for ( final Map.Entry<String, String> entry : entries.entrySet() ) {
            zip.putNextEntry( new ZipEntry( entry.getKey() ) );
            zip.write( utf8( entry.getValue() ) );
            zip.closeEntry();
         }
      }
      return output.toByteArray();
   }

   @Test
   void detectsTheKindByExtension() {
      assertEquals( "pdf", AttachmentTextExtractor.kind( "Spec.PDF" ) );
      assertEquals( "docx", AttachmentTextExtractor.kind( "a.docx" ) );
      assertEquals( "xlsx", AttachmentTextExtractor.kind( "a.xlsx" ) );
      for ( final String extension : new String[] { "txt", "md", "markdown", "json", "csv", "tsv", "ttl", "yaml", "yml", "xml" } ) {
         assertEquals( "text", AttachmentTextExtractor.kind( "file." + extension ), extension );
      }
      assertNull( AttachmentTextExtractor.kind( "a.exe" ) );
      assertNull( AttachmentTextExtractor.kind( "README" ) );
      assertNull( AttachmentTextExtractor.kind( null ) );
      assertNull( AttachmentTextExtractor.kind( "old.doc" ) );
   }

   @Test
   void readsTextFilesAsUtf8WithoutBom() {
      final AttachmentTextExtractor.Extracted extracted = AttachmentTextExtractor.extract( "notes.md",
            utf8( "\uFEFF# Überschrift\r\nZeile 2\r\n" ) );
      assertEquals( "text", extracted.kind() );
      assertEquals( "# Überschrift\nZeile 2", extracted.text() );
      assertNull( extracted.pages() );
      assertFalse( extracted.truncated() );
   }

   @Test
   void rejectsUnsupportedLargeAndEmptyFiles() {
      assertEquals( AiErrorCode.ATTACHMENT_UNSUPPORTED, failure( "virus.exe", utf8( "x" ) ) );
      assertEquals( AiErrorCode.ATTACHMENT_TOO_LARGE,
            failure( "big.txt", new byte[(int) AttachmentTextExtractor.MAX_FILE_SIZE + 1] ) );
      assertEquals( AiErrorCode.ATTACHMENT_EMPTY, failure( "empty.txt", utf8( " \n\t " ) ) );
   }

   @Test
   void truncatesVeryLongTexts() {
      final String text = "a".repeat( AttachmentTextExtractor.MAX_CHARACTERS + 10 );
      final AttachmentTextExtractor.Extracted extracted = AttachmentTextExtractor.extract( "long.txt", utf8( text ) );
      assertTrue( extracted.truncated() );
      assertEquals( AttachmentTextExtractor.MAX_CHARACTERS, extracted.text().length() );
   }

   @Test
   void extractsTheTextOfPdfPages() throws IOException {
      final AttachmentTextExtractor.Extracted extracted = AttachmentTextExtractor.extract( "spec.pdf",
            pdf( "Vehicle speed in km/h", "Position as latitude" ) );
      assertEquals( "pdf", extracted.kind() );
      assertEquals( 2, extracted.pages() );
      assertTrue( extracted.text().startsWith( "[Page 1]\nVehicle speed in km/h" ), extracted.text() );
      assertTrue( extracted.text().contains( "[Page 2]\nPosition as latitude" ), extracted.text() );
      assertNull( extracted.warning() );
   }

   @Test
   void warnsAboutPdfsWithoutText() throws IOException {
      final AttachmentTextExtractor.Extracted extracted = AttachmentTextExtractor.extract( "scan.pdf", pdf( (String) null ) );
      assertEquals( AiAttachment.NO_TEXT, extracted.warning() );
      assertEquals( "", extracted.text() );
      assertEquals( 1, extracted.pages() );
   }

   @Test
   void rejectsBrokenAndProtectedPdfs() throws IOException {
      assertEquals( AiErrorCode.ATTACHMENT_UNREADABLE, failure( "broken.pdf", utf8( "not a pdf" ) ) );
      assertEquals( AiErrorCode.ATTACHMENT_EMPTY, failure( "nothing.pdf", pdf() ) );

      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      try ( final PDDocument document = new PDDocument() ) {
         document.addPage( new PDPage() );
         final StandardProtectionPolicy policy = new StandardProtectionPolicy( "owner", "secret", new AccessPermission() );
         policy.setEncryptionKeyLength( 128 );
         document.protect( policy );
         document.save( output );
      }
      final AiException exception = assertThrows( AiException.class,
            () -> AttachmentTextExtractor.extract( "protected.pdf", output.toByteArray() ) );
      assertEquals( AiErrorCode.ATTACHMENT_UNREADABLE, exception.getCode() );
      assertTrue( exception.getMessage().contains( "password" ) );
   }

   @Test
   void extractsWordDocuments() throws IOException {
      final String document = "<w:document xmlns:w=\"" + W + "\"><w:body>"
            + "<w:p><w:pPr><w:tabs><w:tab w:val=\"left\" w:pos=\"720\"/></w:tabs></w:pPr>"
            + "<w:r><w:t>Movement</w:t></w:r><w:r><w:tab/><w:t xml:space=\"preserve\">Aspect </w:t></w:r></w:p>"
            + "<w:p><w:r><w:t>Line</w:t><w:br/><w:t>break</w:t></w:r></w:p>"
            + "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>speed</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>km/h</w:t></w:r></w:p></w:tc>"
            + "</w:tr></w:tbl></w:body></w:document>";
      final AttachmentTextExtractor.Extracted extracted = AttachmentTextExtractor.extract( "spec.docx",
            zip( Map.of( "word/document.xml", document, "[Content_Types].xml", "<Types/>" ) ) );
      assertEquals( "docx", extracted.kind() );
      assertTrue( extracted.text().startsWith( "Movement\tAspect \nLine\nbreak\n" ), extracted.text() );
      assertTrue( extracted.text().contains( "speed\n\tkm/h" ), extracted.text() );
   }

   @Test
   void rejectsInvalidWordDocuments() throws IOException {
      assertEquals( AiErrorCode.ATTACHMENT_UNREADABLE, failure( "a.docx", utf8( "plain text" ) ) );
      assertEquals( AiErrorCode.ATTACHMENT_UNREADABLE, failure( "a.docx", zip( Map.of( "other.xml", "<x/>" ) ) ) );
      assertEquals( AiErrorCode.ATTACHMENT_UNREADABLE, failure( "a.docx", zip( Map.of( "word/document.xml", "<broken" ) ) ) );
   }

   @Test
   void ignoresExternalEntitiesInOfficeXml() throws IOException {
      final String document = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
            + "<w:document xmlns:w=\"" + W + "\"><w:body><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:body></w:document>";
      final byte[] content = zip( Map.of( "word/document.xml", document ) );
      try {
         final AttachmentTextExtractor.Extracted extracted = AttachmentTextExtractor.extract( "x.docx", content );
         assertFalse( extracted.text().contains( "root:" ) );
      } catch ( final AiException exception ) {
         assertTrue( exception.getCode() == AiErrorCode.ATTACHMENT_UNREADABLE || exception.getCode() == AiErrorCode.ATTACHMENT_EMPTY );
      }
   }

   @Test
   void extractsExcelWorkbooks() throws IOException {
      final Map<String, String> entries = new LinkedHashMap<>();
      entries.put( "xl/workbook.xml", "<workbook xmlns=\"" + S + "\" xmlns:r=\"" + R + "\"><sheets>"
            + "<sheet name=\"Signals\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Units\" sheetId=\"2\" r:id=\"rId2\"/>"
            + "</sheets></workbook>" );
      entries.put( "xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Target=\"/xl/worksheets/sheet2.xml\"/>"
            + "</Relationships>" );
      entries.put( "xl/sharedStrings.xml", "<sst xmlns=\"" + S + "\"><si><t>Name</t></si><si><r><t>Spe</t></r><r><t>ed</t></r></si>"
            + "</sst>" );
      entries.put( "xl/worksheets/sheet1.xml", "<worksheet xmlns=\"" + S + "\"><sheetData>"
            + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"C1\" t=\"inlineStr\"><is><t>Unit</t></is></c></row>"
            + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>1</v></c><c r=\"B2\"><v>42.5</v></c><c r=\"C2\" t=\"b\"><v>1</v></c>"
            + "<c r=\"D2\" t=\"str\"><f>A1</f><v>calc</v></c></row><row r=\"3\"/></sheetData></worksheet>" );
      entries.put( "xl/worksheets/sheet2.xml", "<worksheet xmlns=\"" + S + "\"><sheetData>"
            + "<row><c t=\"s\"><v>9</v></c><c><v>km/h</v></c></row></sheetData></worksheet>" );
      final AttachmentTextExtractor.Extracted extracted = AttachmentTextExtractor.extract( "signals.xlsx", zip( entries ) );
      assertEquals( "xlsx", extracted.kind() );
      assertEquals( 2, extracted.pages() );
      assertTrue( extracted.text().startsWith( "[Sheet Signals]\nName\t\tUnit\nSpeed\t42.5\tTRUE\tcalc\n" ), extracted.text() );
      assertTrue( extracted.text().contains( "[Sheet Units]\n\tkm/h" ), extracted.text() );
   }

   @Test
   void readsSheetsWithoutRelationships() throws IOException {
      final Map<String, String> entries = new LinkedHashMap<>();
      entries.put( "xl/workbook.xml", "<workbook xmlns=\"" + S + "\"><sheets><sheet name=\"Data\"/></sheets></workbook>" );
      entries.put( "xl/worksheets/sheet1.xml", "<worksheet xmlns=\"" + S + "\"><sheetData><row><c><v>1</v></c></row>"
            + "</sheetData></worksheet>" );
      assertEquals( "[Sheet Data]\n1", AttachmentTextExtractor.extract( "a.xlsx", zip( entries ) ).text() );
   }

   @Test
   void rejectsInvalidWorkbooks() throws IOException {
      assertEquals( AiErrorCode.ATTACHMENT_UNREADABLE, failure( "a.xlsx", zip( Map.of( "xl/worksheets/sheet1.xml", "<x/>" ) ) ) );
      assertEquals( AiErrorCode.ATTACHMENT_EMPTY, failure( "a.xlsx",
            zip( Map.of( "xl/workbook.xml", "<workbook xmlns=\"" + S + "\"><sheets/></workbook>" ) ) ) );
   }

   @Test
   void checksTheFileSizeWithOneMessage() {
      AttachmentTextExtractor.checkFileSize( AttachmentTextExtractor.MAX_FILE_SIZE );
      final AiException exception = assertThrows( AiException.class,
            () -> AttachmentTextExtractor.checkFileSize( AttachmentTextExtractor.MAX_FILE_SIZE + 1 ) );
      assertEquals( AiErrorCode.ATTACHMENT_TOO_LARGE, exception.getCode() );
      assertEquals( "The file is larger than 20 MB.", exception.getMessage() );
      assertEquals( AttachmentTextExtractor.TOO_LARGE_MESSAGE, exception.getMessage() );
   }

   @Test
   void convertsCellReferencesToColumns() {
      assertEquals( 0, XlsxSheetReader.column( "A1" ) );
      assertEquals( 25, XlsxSheetReader.column( "Z9" ) );
      assertEquals( 26, XlsxSheetReader.column( "AA10" ) );
      assertEquals( 0, XlsxSheetReader.column( "1" ) );
   }
}
