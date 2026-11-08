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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.esmf.ame.plugin.api.AmePlugin;
import org.eclipse.esmf.ame.plugin.api.ModelGenerator;
import org.eclipse.esmf.ame.plugin.api.ModelImporter;
import org.eclipse.esmf.ame.plugin.api.PluginAction;
import org.eclipse.esmf.ame.services.models.plugins.PluginManifest;
import org.eclipse.esmf.ame.services.models.plugins.PluginState;

import io.micronaut.core.annotation.Nullable;

/**
 * A plugin the backend tried to load when it started.
 *
 * @param id the id of the plugin
 * @param version the loaded version
 * @param directory the directory the plugin was extracted to
 * @param manifest the manifest of the plugin, {@code null} if it could not be read
 * @param state {@link PluginState#ACTIVE} or {@link PluginState#FAILED}
 * @param error why the plugin failed, {@code null} if it is active
 * @param runtime the started backend part, {@code null} if the plugin failed or has no backend part
 */
record LoadedPlugin(
      String id,
      String version,
      Path directory,
      @Nullable PluginManifest manifest,
      PluginState state,
      @Nullable String error,
      @Nullable Runtime runtime
) {
   static LoadedPlugin active( final PluginManifest manifest, final Path directory, final @Nullable Runtime runtime ) {
      return new LoadedPlugin( manifest.id(), manifest.version(), directory, manifest, PluginState.ACTIVE, null, runtime );
   }

   static LoadedPlugin failed( final String id, final String version, final Path directory, final @Nullable PluginManifest manifest,
         final String error ) {
      return new LoadedPlugin( id, version, directory, manifest, PluginState.FAILED, error, null );
   }

   boolean isActive() {
      return state == PluginState.ACTIVE;
   }

   Optional<PluginAction> action( final String actionId ) {
      return lookup( Runtime::actions, actionId );
   }

   Optional<ModelImporter> importer( final String importerId ) {
      return lookup( Runtime::importers, importerId );
   }

   Optional<ModelGenerator> generator( final String generatorId ) {
      return lookup( Runtime::generators, generatorId );
   }

   List<String> actionIds() {
      return ids( Runtime::actions );
   }

   List<String> importerIds() {
      return ids( Runtime::importers );
   }

   List<String> generatorIds() {
      return ids( Runtime::generators );
   }

   private <T> Optional<T> lookup( final Function<Runtime, Map<String, T>> extensions, final String extensionId ) {
      return runtime == null ? Optional.empty() : Optional.ofNullable( extensions.apply( runtime ).get( extensionId ) );
   }

   private List<String> ids( final Function<Runtime, Map<String, ?>> extensions ) {
      return runtime == null ? List.of() : extensions.apply( runtime ).keySet().stream().sorted().toList();
   }

   /**
    * The started backend part of a plugin.
    *
    * @param plugin the started plugin
    * @param classLoader the class loader of the plugin
    * @param actions the actions of the plugin by id
    * @param importers the importers of the plugin by id
    * @param generators the generators of the plugin by id
    */
   record Runtime(
         AmePlugin plugin,
         PluginClassLoader classLoader,
         Map<String, PluginAction> actions,
         Map<String, ModelImporter> importers,
         Map<String, ModelGenerator> generators
   ) {
      Runtime {
         actions = Map.copyOf( actions );
         importers = Map.copyOf( importers );
         generators = Map.copyOf( generators );
      }
   }
}
