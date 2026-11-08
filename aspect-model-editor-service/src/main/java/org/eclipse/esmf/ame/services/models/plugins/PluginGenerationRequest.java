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

import java.util.Map;

import org.eclipse.esmf.ame.plugin.api.GenerationSource;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Asks a generator of a plugin to create files from the Aspect Model open in the editor.
 *
 * @param model the Aspect Model open in the editor
 * @param selectedElement the URN of the element selected in the editor, {@code null} if no element is selected
 * @param options the options chosen in the user interface of the plugin
 */
@Serdeable
@Introspected
public record PluginGenerationRequest( @Nullable PluginModel model, @Nullable String selectedElement,
      @Nullable Map<String, String> options ) {
   public PluginGenerationRequest {
      options = options == null ? Map.of() : Map.copyOf( options );
   }

   /**
    * Creates the source passed to a generator.
    *
    * @return the model, the selected element and the options of this request
    * @throws IllegalArgumentException if the request contains no complete model
    */
   public GenerationSource toGenerationSource() {
      if ( model == null || model.namespace() == null || model.version() == null || model.fileName() == null || model.content() == null ) {
         throw new IllegalArgumentException( "The request must contain the Aspect Model with namespace, version, fileName and content." );
      }
      return new GenerationSource( model.namespace(), model.version(), model.fileName(), model.content(), selectedElement, options );
   }
}
