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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class XlsxSheetReaderTest {
   private static String text( final String rows, final List<String> sharedStrings ) {
      return XlsxSheetReader.text( ( "<worksheet><sheetData>" + rows + "</sheetData></worksheet>" ).getBytes( StandardCharsets.UTF_8 ),
            sharedStrings );
   }

   @Test
   void keepsGapsBetweenCellsAsEmptyColumns() {
      assertEquals( "a\t\tc\n", text( "<row><c r=\"A1\"><v>a</v></c><c r=\"C1\"><v>c</v></c></row>", List.of() ) );
      assertEquals( "\tb\n", text( "<row><c r=\"B2\"><v>b</v></c></row>", List.of() ) );
   }

   @Test
   void skipsEmptyRowsAndCells() {
      assertEquals( "x\n", text( "<row/><row><c r=\"A2\"/><c><v>x</v></c></row>", List.of() ) );
   }

   @Test
   void resolvesSharedStringsBooleansAndInlineStrings() {
      assertEquals( "two\tTRUE\tinline\n", text( "<row><c t=\"s\"><v>1</v></c><c t=\"b\"><v>1</v></c>"
            + "<c t=\"inlineStr\"><is><t>inline</t></is></c></row>", List.of( "one", "two" ) ) );
   }

   @Test
   void handlesInvalidCellValues() {
      assertEquals( "", XlsxSheetReader.cellValue( "s", "5", List.of( "one" ) ) );
      assertEquals( "x", XlsxSheetReader.cellValue( "s", "x", List.of( "one" ) ) );
      assertEquals( "FALSE", XlsxSheetReader.cellValue( "b", "0", List.of() ) );
      assertEquals( "3.5", XlsxSheetReader.cellValue( null, "3.5", List.of() ) );
   }
}
