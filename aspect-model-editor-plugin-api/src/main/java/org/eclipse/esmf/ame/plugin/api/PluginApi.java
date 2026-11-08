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

/**
 * Version information of this API.
 */
public final class PluginApi {
   /**
    * The version of the plugin API. It is increased with every incompatible change; a plugin declares the version it was
    * built for as {@code pluginApiVersion} in its {@code plugin.json} and is only loaded by editors supporting it.
    */
   public static final int VERSION = 1;

   private PluginApi() {
   }
}
