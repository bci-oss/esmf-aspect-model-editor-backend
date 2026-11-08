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
package org.eclipse.esmf.ame.examples.generator;

import java.util.List;

import org.eclipse.esmf.ame.plugin.api.AmePlugin;
import org.eclipse.esmf.ame.plugin.api.ModelGenerator;
import org.eclipse.esmf.ame.plugin.api.PluginContext;

/**
 * Example generator plugin exporting the Aspect of the open Aspect Model as JSON description.
 * <p>
 * It offers the generator {@code json}. The editor passes the open Aspect Model and the model element selected in the
 * editor, if any.
 * </p>
 */
public class GeneratorPlugin implements AmePlugin {
   @Override
   public void start( final PluginContext context ) {
      context.logger().log( System.Logger.Level.INFO, "Example generator plugin {0} started", context.pluginVersion() );
   }

   @Override
   public List<ModelGenerator> generators() {
      return List.of( new JsonDescriptionGenerator() );
   }
}
