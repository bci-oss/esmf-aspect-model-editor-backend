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

import java.util.List;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The installed plugins.
 *
 * @param pluginApiVersion the version of the plugin API the editor supports
 * @param plugins the installed plugins, sorted by name
 * @param restartRequired whether a restart of the backend is needed for changes to take effect, including plugins which
 *       were removed but are still running
 */
@Serdeable
@Introspected
public record PluginsOverview( int pluginApiVersion, List<PluginStatus> plugins, boolean restartRequired ) {
   public PluginsOverview {
      plugins = List.copyOf( plugins );
   }
}
