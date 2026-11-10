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
package org.eclipse.esmf.ame.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class SammCharacteristicsTest {
   private static List<String> names( final List<SammCharacteristics.Entry> entries ) {
      return entries.stream().map( SammCharacteristics.Entry::name ).toList();
   }

   @Test
   void entriesAreUniqueCharacteristicNamespaceElements() {
      final List<String> names = names( SammCharacteristics.ENTRIES );

      assertEquals( names.size(), Set.copyOf( names ).size() );
      assertTrue( names.stream().allMatch( name -> name.startsWith( "samm-c:" ) ) );
      assertEquals( Set.of( "class", "constraint", "instance" ),
            SammCharacteristics.ENTRIES.stream().map( SammCharacteristics.Entry::kind ).collect( Collectors.toSet() ) );
      assertTrue( SammCharacteristics.ENTRIES.stream().noneMatch( entry -> entry.description().isBlank() ) );
   }

   @Test
   void returnsAllEntriesWithoutQuery() {
      assertEquals( SammCharacteristics.ENTRIES, SammCharacteristics.search( null ) );
      assertEquals( SammCharacteristics.ENTRIES, SammCharacteristics.search( "" ) );
      assertEquals( SammCharacteristics.ENTRIES, SammCharacteristics.search( "   " ) );
   }

   @Test
   void searchesNamesCaseInsensitivelyAndTrimmed() {
      assertEquals( List.of( "samm-c:Measurement" ), names( SammCharacteristics.search( "  MEASUREMENT " ) ) );
      assertEquals( List.of( "samm-c:Set", "samm-c:SortedSet" ),
            names( SammCharacteristics.search( "set" ) ).stream().filter( name -> name.endsWith( "Set" ) ).toList() );
   }

   @Test
   void searchesDescriptions() {
      assertEquals( List.of( "samm-c:Language" ), names( SammCharacteristics.search( "ISO 639" ) ) );

      final List<String> constraints = names( SammCharacteristics.search( "constraint" ) );
      assertTrue( constraints.contains( "samm-c:Trait" ) );
      assertTrue( constraints.containsAll( SammCharacteristics.ENTRIES.stream().filter( entry -> "constraint".equals( entry.kind() ) )
            .map( SammCharacteristics.Entry::name ).toList() ) );
   }

   @Test
   void keepsTheOrderOfTheEntries() {
      final List<String> names = names( SammCharacteristics.search( "collection" ) );

      assertEquals( names.stream().sorted( ( a, b ) -> Integer.compare( indexOf( a ), indexOf( b ) ) ).toList(), names );
      assertFalse( names.isEmpty() );
   }

   @Test
   void returnsNothingForUnknownTerms() {
      assertTrue( SammCharacteristics.search( "no such characteristic" ).isEmpty() );
   }

   private static int indexOf( final String name ) {
      return names( SammCharacteristics.ENTRIES ).indexOf( name );
   }
}
