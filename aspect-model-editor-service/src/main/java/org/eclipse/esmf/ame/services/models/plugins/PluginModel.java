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

import org.eclipse.esmf.ame.plugin.api.ImportedModel;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * An Aspect Model exchanged with the user interface of a plugin: created by an importer or passed to a generator.
 *
 * @param namespace the namespace of the model, e.g. {@code org.example}
 * @param version the version of the namespace, e.g. {@code 1.0.0}
 * @param fileName the name of the file of the model, e.g. {@code Movement.ttl}
 * @param content the model in RDF/Turtle
 */
@Serdeable
@Introspected
public record PluginModel( String namespace, String version, String fileName, String content ) {
   /**
    * Copies a model created by a plugin.
    *
    * @param model the model created by the plugin
    * @return the copy
    */
   public static PluginModel of( final ImportedModel model ) {
      return new PluginModel( model.namespace(), model.version(), model.fileName(), model.content() );
   }
}
