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

package org.eclipse.esmf.ame.services.models.plugins;

import java.util.List;

import org.eclipse.esmf.ame.plugin.api.ImportResult;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The result of an import by a plugin, as returned to its user interface.
 *
 * @param models the Aspect Models created from the file
 * @param warnings problems the user should know about
 */
@Serdeable
@Introspected
public record PluginImportResult( List<PluginModel> models, List<String> warnings ) {
   public PluginImportResult {
      models = List.copyOf( models );
      warnings = List.copyOf( warnings );
   }

   /**
    * Copies the result returned by a plugin.
    *
    * @param result the result of the plugin
    * @return the copy
    */
   public static PluginImportResult of( final ImportResult result ) {
      return new PluginImportResult(
            result.models().stream().map( PluginModel::of ).toList(),
            result.warnings() );
   }
}
