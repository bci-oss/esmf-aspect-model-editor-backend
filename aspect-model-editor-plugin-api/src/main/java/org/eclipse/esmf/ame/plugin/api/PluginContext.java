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

import java.nio.file.Path;

/**
 * What the editor offers a plugin. Plugins only access the editor through this interface, never through its internal
 * classes, which are not visible to them.
 */
public interface PluginContext {
   /**
    * @return the id of the plugin as declared in its {@code plugin.json}
    */
   String pluginId();

   /**
    * @return the version of the plugin as declared in its {@code plugin.json}
    */
   String pluginVersion();

   /**
    * A directory the plugin may store data in, e.g. caches or settings. It is kept when the plugin is updated and deleted
    * when the plugin is removed.
    *
    * @return the existing data directory of the plugin
    */
   Path dataDirectory();

   /**
    * @return read access to the models of the workspaces
    */
   WorkspaceAccess workspace();

   /**
    * @return a logger writing to the log of the editor backend, prefixed with the id of the plugin
    */
   System.Logger logger();
}
