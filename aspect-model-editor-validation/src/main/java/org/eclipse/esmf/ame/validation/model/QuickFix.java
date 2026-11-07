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

package org.eclipse.esmf.ame.validation.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A fix of a violation that a client can apply automatically: the value of a property of an element is replaced.
 *
 * @param description what the fix does
 * @param element the URN of the element whose property value is replaced
 * @param property the URI of the property
 * @param oldValue the value to replace
 * @param newValue the replacement
 */
@Serdeable
@Introspected
public record QuickFix(
      String description,
      String element,
      String property,
      LiteralValue oldValue,
      LiteralValue newValue
) {
   /**
    * An RDF literal.
    *
    * @param value the lexical form
    * @param datatype the URI of the datatype
    * @param language the language tag, empty if the literal has none
    */
   @Serdeable
   @Introspected
   public record LiteralValue(
         String value,
         String datatype,
         String language
   ) {
   }
}
