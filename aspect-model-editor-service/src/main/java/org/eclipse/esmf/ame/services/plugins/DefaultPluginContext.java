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

package org.eclipse.esmf.ame.services.plugins;

import java.nio.file.Path;

import org.eclipse.esmf.ame.plugin.api.PluginContext;
import org.eclipse.esmf.ame.plugin.api.WorkspaceAccess;

/**
 * The editor as seen by one plugin.
 *
 * @param pluginId the id of the plugin
 * @param pluginVersion the version of the plugin
 * @param dataDirectory the data directory of the plugin
 * @param workspace read access to the models of the workspaces
 * @param logger the logger of the plugin
 */
record DefaultPluginContext( String pluginId, String pluginVersion, Path dataDirectory, WorkspaceAccess workspace,
      System.Logger logger ) implements PluginContext {
}
