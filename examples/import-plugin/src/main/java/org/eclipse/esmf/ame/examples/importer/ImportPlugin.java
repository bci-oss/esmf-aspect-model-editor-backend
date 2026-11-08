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
package org.eclipse.esmf.ame.examples.importer;

import java.util.List;

import org.eclipse.esmf.ame.plugin.api.AmePlugin;
import org.eclipse.esmf.ame.plugin.api.ModelImporter;
import org.eclipse.esmf.ame.plugin.api.PluginAction;
import org.eclipse.esmf.ame.plugin.api.PluginContext;

/**
 * Example import plugin creating Aspect Models from a JSON description of an Aspect.
 * <p>
 * It offers the importer {@code json} and the action {@code existing-models}, which lists the model files of the
 * workspaces so that the user interface can warn before a model is replaced.
 * </p>
 */
public class ImportPlugin implements AmePlugin {
   private PluginContext context;

   @Override
   public void start( final PluginContext context ) {
      this.context = context;
      context.logger().log( System.Logger.Level.INFO, "Example import plugin {0} started", context.pluginVersion() );
   }

   @Override
   public List<ModelImporter> importers() {
      return List.of( new JsonDescriptionImporter() );
   }

   @Override
   public List<PluginAction> actions() {
      return List.of( new ExistingModelsAction( context.workspace() ) );
   }
}
