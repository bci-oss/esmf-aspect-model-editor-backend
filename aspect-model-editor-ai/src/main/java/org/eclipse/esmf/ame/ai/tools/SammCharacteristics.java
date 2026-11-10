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

import java.util.List;
import java.util.Locale;

/**
 * The characteristics and characteristic classes of the SAMM characteristic namespace, as reference for the AI.
 */
final class SammCharacteristics {
   record Entry( String name, String kind, String description ) {
   }

   static final List<Entry> ENTRIES = List.of(
         new Entry( "samm-c:Trait", "class", "Adds constraints (samm-c:baseCharacteristic, samm-c:constraint) to a characteristic." ),
         new Entry( "samm-c:Quantifiable", "class", "A value that can be quantified, optionally with a samm-c:unit." ),
         new Entry( "samm-c:Measurement", "class", "A measured value with a mandatory samm-c:unit." ),
         new Entry( "samm-c:Duration", "class", "A time duration with a time unit (samm-c:unit)." ),
         new Entry( "samm-c:Enumeration", "class", "A value from a fixed list (samm-c:values)." ),
         new Entry( "samm-c:State", "class", "An enumeration with a samm-c:defaultValue." ),
         new Entry( "samm-c:Collection", "class", "A group of values; unordered, duplicates allowed (samm-c:elementCharacteristic optional)." ),
         new Entry( "samm-c:List", "class", "An ordered collection that allows duplicates." ),
         new Entry( "samm-c:Set", "class", "An unordered collection without duplicates." ),
         new Entry( "samm-c:SortedSet", "class", "An ordered collection without duplicates." ),
         new Entry( "samm-c:TimeSeries", "class", "A list of samm-e:TimeSeriesEntity values (timestamp and value)." ),
         new Entry( "samm-c:Code", "class", "A coded string value, e.g. an identifier or error code." ),
         new Entry( "samm-c:Either", "class", "A value that is one of two characteristics (samm-c:left, samm-c:right)." ),
         new Entry( "samm-c:SingleEntity", "class", "A value of an entity type (samm:dataType is an entity)." ),
         new Entry( "samm-c:StructuredValue", "class", "A string with a structure given by samm-c:deconstructionRule and samm-c:elements." ),
         new Entry( "samm-c:RangeConstraint", "constraint", "Limits a value to samm-c:minValue / samm-c:maxValue." ),
         new Entry( "samm-c:LengthConstraint", "constraint", "Limits the length of a string or collection (samm-c:minValue / samm-c:maxValue)." ),
         new Entry( "samm-c:RegularExpressionConstraint", "constraint", "Requires a string to match the regular expression samm:value." ),
         new Entry( "samm-c:EncodingConstraint", "constraint", "Restricts the encoding of a string, e.g. samm:UTF-8." ),
         new Entry( "samm-c:LanguageConstraint", "constraint", "Restricts a multi-language text to samm-c:languageCode." ),
         new Entry( "samm-c:LocaleConstraint", "constraint", "Restricts a value to samm-c:localeCode." ),
         new Entry( "samm-c:FixedPointConstraint", "constraint", "Fixes samm-c:scale and samm-c:integer digits of a decimal." ),
         new Entry( "samm-c:Text", "instance", "Any string (xsd:string)." ),
         new Entry( "samm-c:Boolean", "instance", "A boolean value (xsd:boolean)." ),
         new Entry( "samm-c:Timestamp", "instance", "A date and time (xsd:dateTime)." ),
         new Entry( "samm-c:MultiLanguageText", "instance", "A text in several languages (rdf:langString)." ),
         new Entry( "samm-c:ResourcePath", "instance", "A path to a resource (xsd:anyURI)." ),
         new Entry( "samm-c:MimeType", "instance", "A MIME type, e.g. application/json." ),
         new Entry( "samm-c:UnitReference", "instance", "A reference to a unit of the SAMM unit catalog (samm:curie)." ),
         new Entry( "samm-c:Language", "instance", "An ISO 639-1 language code, e.g. de." ),
         new Entry( "samm-c:Locale", "instance", "An IETF BCP 47 locale code, e.g. de-DE." ) );

   private SammCharacteristics() {
   }

   static List<Entry> search( final String query ) {
      final String text = query == null ? "" : query.trim().toLowerCase( Locale.ROOT );
      return ENTRIES.stream()
            .filter( entry -> text.isEmpty() || ( entry.name() + " " + entry.description() ).toLowerCase( Locale.ROOT ).contains( text ) )
            .toList();
   }
}
