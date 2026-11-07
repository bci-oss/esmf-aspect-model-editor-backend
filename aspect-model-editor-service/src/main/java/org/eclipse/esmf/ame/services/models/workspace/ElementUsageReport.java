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
package org.eclipse.esmf.ame.services.models.workspace;

import java.util.List;

import org.eclipse.esmf.ame.model.UnreadableModelFile;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Where a model element is used in the files of all sources.
 *
 * @param urn the URN of the used element
 * @param usages the elements using it, sorted by file and URN
 * @param unreadable the files that could not be checked, since they cannot be read
 */
@Serdeable
@Introspected
public record ElementUsageReport(
      String urn,
      List<ElementUsage> usages,
      List<UnreadableModelFile> unreadable
) {
   public ElementUsageReport {
      usages = usages == null ? List.of() : List.copyOf( usages );
      unreadable = unreadable == null ? List.of() : List.copyOf( unreadable );
   }

   /**
    * Whether the element is used outside of the file defining it.
    *
    * @return true if an element of another file uses it
    */
   public boolean usedExternally() {
      return usages.stream().anyMatch( ElementUsage::external );
   }
}
