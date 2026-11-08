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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * An installed plugin as shown to the user.
 *
 * @param id the id of the plugin
 * @param name the name of the plugin
 * @param version the installed version
 * @param description the description of the plugin
 * @param state whether the plugin is running now
 * @param enabled whether the plugin is started with the backend
 * @param restartRequired whether a restart of the backend is needed for the installed version and the enabled flag to
 *       take effect
 * @param error why the plugin failed, {@code null} unless it failed
 * @param frontend the user interface of the plugin, {@code null} if it has none
 * @param commands the commands of the plugin
 * @param actions the ids of the actions the running plugin offers
 * @param importers the ids of the importers the running plugin offers
 * @param generators the ids of the generators the running plugin offers
 */
@Serdeable
@Introspected
public record PluginStatus(
      String id,
      String name,
      String version,
      @Nullable String description,
      PluginState state,
      boolean enabled,
      boolean restartRequired,
      @Nullable String error,
      @Nullable PluginManifest.Frontend frontend,
      List<PluginManifest.CommandContribution> commands,
      List<String> actions,
      List<String> importers,
      List<String> generators
) {
   public PluginStatus {
      commands = List.copyOf( commands );
      actions = List.copyOf( actions );
      importers = List.copyOf( importers );
      generators = List.copyOf( generators );
   }
}
