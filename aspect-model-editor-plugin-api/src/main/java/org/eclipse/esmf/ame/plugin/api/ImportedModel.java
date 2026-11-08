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

import java.util.Objects;

/**
 * An Aspect Model created by an import.
 *
 * @param namespace the namespace of the model, e.g. {@code org.example}
 * @param version the version of the namespace, e.g. {@code 1.0.0}
 * @param fileName the name of the file the model should be saved as, e.g. {@code Movement.ttl}
 * @param content the model in RDF/Turtle
 */
public record ImportedModel( String namespace, String version, String fileName, String content ) {
   public ImportedModel {
      Objects.requireNonNull( namespace, "namespace" );
      Objects.requireNonNull( version, "version" );
      Objects.requireNonNull( fileName, "fileName" );
      Objects.requireNonNull( content, "content" );
   }
}
