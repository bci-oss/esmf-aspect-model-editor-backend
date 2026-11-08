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
import java.util.Map;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The {@code plugin.json} at the root of a plugin archive. All fields are optional here so that a missing field is
 * reported by the validation with a helpful message instead of a parsing error.
 *
 * @param id the unique id of the plugin, e.g. {@code example-import}
 * @param name the name shown to the user
 * @param version the version of the plugin, e.g. {@code 1.0.0}
 * @param description a short description shown to the user
 * @param pluginApiVersion the version of the plugin API the plugin was built for
 * @param frontend the user interface of the plugin, {@code null} for plugins without one
 * @param contributions the entries the plugin adds to the editor, e.g. commands
 */
@Serdeable
@Introspected
public record PluginManifest(
      @Nullable String id,
      @Nullable String name,
      @Nullable String version,
      @Nullable String description,
      @Nullable Integer pluginApiVersion,
      @Nullable Frontend frontend,
      @Nullable Contributions contributions
) {
   /**
    * The commands of the plugin.
    *
    * @return the commands, empty if there are none
    */
   public List<CommandContribution> commands() {
      return contributions == null ? List.of() : contributions.commands();
   }

   /**
    * The user interface of a plugin: a JavaScript module defining a custom element.
    *
    * @param entry the path of the module within the archive, e.g. {@code frontend/main.js}
    * @param element the name of the custom element the module defines, e.g. {@code ame-plugin-example-import}
    */
   @Serdeable
   @Introspected
   public record Frontend( @Nullable String entry, @Nullable String element ) {
   }

   /**
    * The entries a plugin adds to the editor.
    *
    * @param commands the commands opening the user interface of the plugin
    */
   @Serdeable
   @Introspected
   public record Contributions( @Nullable List<CommandContribution> commands ) {
      public Contributions {
         commands = commands == null ? List.of() : List.copyOf( commands );
      }
   }

   /**
    * A command opening the user interface of the plugin, e.g. to import a file or to generate files from the open
    * Aspect Model. The plugin decides where the command is offered to the user.
    *
    * @param id the id of the command, unique within the plugin; passed to the user interface
    * @param kind what the command does, one of {@link #KINDS}
    * @param label the label shown to the user
    * @param labels translations of the label by language, e.g. {@code {"de": "Aus JSON importieren"}}
    * @param icon the name of a Material icon shown in the toolbar; a default of the kind if not set
    * @param placements where the command is offered, any of {@link #PLACEMENTS}; {@value #PLACEMENT_MENU} if not set
    * @param menu the menu of the menu bar the command is shown in, one of {@link #MENUS}; a default of the kind if not
    *       set
    */
   @Serdeable
   @Introspected
   public record CommandContribution(
         @Nullable String id,
         @Nullable String kind,
         @Nullable String label,
         @Nullable Map<String, String> labels,
         @Nullable String icon,
         @Nullable List<String> placements,
         @Nullable String menu
   ) {
      public static final String KIND_IMPORT = "import";
      public static final String KIND_GENERATE = "generate";
      public static final List<String> KINDS = List.of( KIND_IMPORT, KIND_GENERATE );

      public static final String PLACEMENT_MENU = "menu";
      public static final String PLACEMENT_TOOLBAR = "toolbar";
      public static final List<String> PLACEMENTS = List.of( PLACEMENT_MENU, PLACEMENT_TOOLBAR );

      public static final String MENU_IMPORT = "file/import";
      public static final String MENU_GENERATE = "generate";
      public static final String MENU_PLUGINS = "plugins";
      public static final List<String> MENUS = List.of( MENU_IMPORT, MENU_GENERATE, MENU_PLUGINS );

      public CommandContribution {
         labels = labels == null ? Map.of() : Map.copyOf( labels );
         icon = isBlank( icon ) ? ( KIND_GENERATE.equals( kind ) ? "output" : "upload_file" ) : icon;
         placements = placements == null || placements.isEmpty() ? List.of( PLACEMENT_MENU ) : List.copyOf( placements );
         menu = isBlank( menu ) ? ( KIND_GENERATE.equals( kind ) ? MENU_GENERATE : MENU_IMPORT ) : menu;
      }

      private static boolean isBlank( final String value ) {
         return value == null || value.isBlank();
      }
   }
}
