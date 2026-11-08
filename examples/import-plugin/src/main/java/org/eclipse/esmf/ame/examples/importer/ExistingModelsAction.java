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

import org.eclipse.esmf.ame.plugin.api.PluginAction;
import org.eclipse.esmf.ame.plugin.api.PluginException;
import org.eclipse.esmf.ame.plugin.api.WorkspaceAccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lists the model files of the workspaces as JSON array of {@code {namespace, version, fileName}}.
 */
class ExistingModelsAction implements PluginAction {
   private static final ObjectMapper MAPPER = new ObjectMapper();

   private final WorkspaceAccess workspace;

   ExistingModelsAction( final WorkspaceAccess workspace ) {
      this.workspace = workspace;
   }

   @Override
   public String id() {
      return "existing-models";
   }

   @Override
   public String handle( final String payload ) throws PluginException {
      try {
         return MAPPER.writeValueAsString( workspace.files() );
      } catch ( final JsonProcessingException e ) {
         throw new PluginException( "The workspace files could not be listed.", e );
      }
   }
}
