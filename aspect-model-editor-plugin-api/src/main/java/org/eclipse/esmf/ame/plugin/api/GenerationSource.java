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

package org.eclipse.esmf.ame.plugin.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Aspect Model a generator creates files from: a copy of the model open in the editor, including unsaved changes.
 *
 * @param namespace the namespace of the model, e.g. {@code org.example}
 * @param version the version of the namespace, e.g. {@code 1.0.0}
 * @param fileName the name of the file of the model, e.g. {@code Movement.ttl}
 * @param content the model in RDF/Turtle
 * @param selectedElementUrn the URN of the element selected in the editor, {@code null} if no element is selected
 * @param options the options chosen in the frontend of the plugin
 */
public record GenerationSource( String namespace, String version, String fileName, String content, String selectedElementUrn,
      Map<String, String> options ) {
   public GenerationSource {
      Objects.requireNonNull( namespace, "namespace" );
      Objects.requireNonNull( version, "version" );
      Objects.requireNonNull( fileName, "fileName" );
      Objects.requireNonNull( content, "content" );
      options = Map.copyOf( Objects.requireNonNull( options, "options" ) );
   }

   /**
    * The element selected in the editor.
    *
    * @return the URN of the element, empty if no element is selected
    */
   public Optional<String> selectedElement() {
      return Optional.ofNullable( selectedElementUrn );
   }
}
