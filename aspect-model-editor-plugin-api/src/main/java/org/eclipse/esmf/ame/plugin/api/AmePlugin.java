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

import java.util.List;

/**
 * Entry point of the backend part of a plugin.
 * <p>
 * The editor finds the implementation with {@link java.util.ServiceLoader}: the plugin JAR has to list it in
 * {@code META-INF/services/org.eclipse.esmf.ame.plugin.api.AmePlugin}. Each plugin gets its own class loader that only
 * shares this API and the Java runtime with the editor, so plugins bring their own libraries in {@code backend/}.
 * </p>
 * <p>
 * Plugins are started once when the backend starts and stopped when it stops; installing, enabling or removing a plugin
 * takes effect after the backend was restarted.
 * </p>
 */
public interface AmePlugin {
   /**
    * Called once after the plugin was loaded, before any of its actions, importers or generators is used. A plugin that
    * throws here is reported as failed and not used; the editor starts anyway.
    *
    * @param context access to the editor for this plugin
    * @throws PluginException if the plugin cannot be started
    */
   default void start( final PluginContext context ) throws PluginException {
   }

   /**
    * Called once when the backend stops. Releases resources such as threads or open files.
    */
   default void stop() {
   }

   /**
    * The actions the frontend of the plugin can call with {@code host.backend.action(id, payload)}.
    *
    * @return the actions, with unique ids
    */
   default List<PluginAction> actions() {
      return List.of();
   }

   /**
    * The importers the frontend of the plugin can call with {@code host.backend.importModel(id, file, options)}.
    *
    * @return the importers, with unique ids
    */
   default List<ModelImporter> importers() {
      return List.of();
   }

   /**
    * The generators the frontend of the plugin can call with {@code host.backend.generate(id, options)}.
    *
    * @return the generators, with unique ids
    */
   default List<ModelGenerator> generators() {
      return List.of();
   }
}
