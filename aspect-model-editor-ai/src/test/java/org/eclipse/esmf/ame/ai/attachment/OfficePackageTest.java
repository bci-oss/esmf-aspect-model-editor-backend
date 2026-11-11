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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;

import org.junit.jupiter.api.Test;

class OfficePackageTest {
   @Test
   void readsStreamsUpToTheLimit() throws IOException {
      final byte[] data = new byte[100];
      assertArrayEquals( data, OfficePackage.read( new ByteArrayInputStream( data ), 100 ) );
      final AiException exception = assertThrows( AiException.class,
            () -> OfficePackage.read( new ByteArrayInputStream( data ), 99 ) );
      assertEquals( AiErrorCode.ATTACHMENT_TOO_LARGE, exception.getCode() );
      assertEquals( "The unpacked content of the file is too large.", exception.getMessage() );
   }

   @Test
   void unzipsOnlyTheAcceptedEntries() throws IOException {
      final byte[] zip = AttachmentTextExtractorTest.zip( Map.of( "a.xml", "<a/>", "b.xml", "<b/>" ) );
      final Map<String, byte[]> entries = OfficePackage.unzip( zip, "Word", "a.xml"::equals );
      assertEquals( List.of( "a.xml" ), List.copyOf( entries.keySet() ) );
      assertEquals( "<a/>", new String( entries.get( "a.xml" ), StandardCharsets.UTF_8 ) );
   }

   @Test
   void rejectsPackagesWithoutAcceptedEntries() throws IOException {
      final byte[] zip = AttachmentTextExtractorTest.zip( Map.of( "a.xml", "<a/>" ) );
      final AiException exception = assertThrows( AiException.class, () -> OfficePackage.unzip( zip, "Word", name -> false ) );
      assertEquals( AiErrorCode.ATTACHMENT_UNREADABLE, exception.getCode() );
      assertEquals( "The file is not a valid Word document.", exception.getMessage() );
   }

   @Test
   void passesStartAndEndElementsToTheHandler() {
      final List<String> events = new ArrayList<>();
      OfficePackage.parse( "<a x=\"1\"><b/></a>".getBytes( StandardCharsets.UTF_8 ), "Excel", reader -> events.add(
            reader.getEventType() + reader.getLocalName() + ( reader.isStartElement() ? OfficePackage.attribute( reader, "x" ) : "" ) ) );
      assertEquals( List.of( "1a1", "1bnull", "2b", "2a" ), events );
   }

   @Test
   void rejectsMalformedXml() {
      final AiException exception = assertThrows( AiException.class,
            () -> OfficePackage.parse( "<a>".getBytes( StandardCharsets.UTF_8 ), "Excel", reader -> {
            } ) );
      assertEquals( AiErrorCode.ATTACHMENT_UNREADABLE, exception.getCode() );
      assertEquals( "The Excel file could not be read.", exception.getMessage() );
   }

   @Test
   void keepsAiExceptionsWhenMappingFailures() {
      final AiException original = new AiException( AiErrorCode.ATTACHMENT_TOO_LARGE, "too large" );
      assertSame( original, OfficePackage.unreadable( "PDF", original ) );
      final AiException mapped = OfficePackage.unreadable( "PDF", new IOException( "broken" ) );
      assertEquals( AiErrorCode.ATTACHMENT_UNREADABLE, mapped.getCode() );
      assertEquals( "The PDF file could not be read.", mapped.getMessage() );
   }
}
