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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.esmf.ame.exceptions.InvalidPluginException;
import org.eclipse.esmf.ame.plugin.api.PluginApi;
import org.eclipse.esmf.ame.services.models.plugins.PluginManifest;
import org.eclipse.esmf.ame.services.models.plugins.PluginManifest.Frontend;
import org.eclipse.esmf.ame.services.models.plugins.PluginManifest.CommandContribution;

import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

/**
 * Reads and validates the {@code plugin.json} of an extracted plugin archive.
 * <p>
 * An archive contains the {@value #MANIFEST} at its root, the JARs of the backend part below {@value #BACKEND} and the
 * user interface below {@value #FRONTEND}. All problems are reported at once, so that a plugin author can fix them in
 * one go.
 * </p>
 */
@Singleton
public class PluginManifestReader {
   public static final String MANIFEST = "plugin.json";
   public static final String BACKEND = "backend";
   public static final String FRONTEND = "frontend";

   private static final Pattern ID = Pattern.compile( "[a-z0-9][a-z0-9-]{1,62}" );
   private static final Pattern VERSION = Pattern.compile( "[0-9A-Za-z][0-9A-Za-z.+_-]{0,63}" );
   private static final Pattern CUSTOM_ELEMENT = Pattern.compile( "[a-z][a-z0-9._]*-[a-z0-9._-]*" );
   private static final Pattern CONTRIBUTION_ID = Pattern.compile( "[A-Za-z0-9._-]{1,64}" );
   private static final Pattern ICON = Pattern.compile( "[a-z0-9_]{1,64}" );

   private final JsonMapper jsonMapper;

   public PluginManifestReader( final JsonMapper jsonMapper ) {
      this.jsonMapper = jsonMapper;
   }

   /**
    * Reads and validates the manifest of an extracted plugin.
    *
    * @param pluginDirectory the directory the plugin archive was extracted to
    * @return the valid manifest
    * @throws InvalidPluginException if the manifest is missing or invalid
    */
   public PluginManifest read( final Path pluginDirectory ) {
      final PluginManifest manifest = parse( pluginDirectory.resolve( MANIFEST ) );
      final List<String> problems = new ArrayList<>();
      validateIdentity( manifest, problems );
      validateFrontend( pluginDirectory, manifest.frontend(), problems );
      validateCommands( manifest, problems );
      if ( manifest.frontend() == null && !hasBackend( pluginDirectory ) ) {
         problems.add( "the plugin contains neither JARs in '" + BACKEND + "/' nor a 'frontend'" );
      }
      if ( !problems.isEmpty() ) {
         throw new InvalidPluginException( "The plugin is invalid: " + String.join( "; ", problems ) + "." );
      }
      return manifest;
   }

   /**
    * Whether an extracted plugin has a backend part.
    *
    * @param pluginDirectory the directory the plugin archive was extracted to
    * @return {@code true} if there is at least one JAR below {@value #BACKEND}
    */
   public static boolean hasBackend( final Path pluginDirectory ) {
      return !PluginClassLoader.backendJars( pluginDirectory.resolve( BACKEND ) ).isEmpty();
   }

   private PluginManifest parse( final Path manifestFile ) {
      if ( !Files.isRegularFile( manifestFile ) ) {
         throw new InvalidPluginException( "The archive is not a plugin: '" + MANIFEST + "' is missing at its root." );
      }
      final PluginManifest manifest;
      try {
         manifest = jsonMapper.readValue( Files.readString( manifestFile, StandardCharsets.UTF_8 ), PluginManifest.class );
      } catch ( final IOException | RuntimeException e ) {
         throw new InvalidPluginException( "'" + MANIFEST + "' is not valid JSON: " + e.getMessage(), e );
      }
      if ( manifest == null ) {
         throw new InvalidPluginException( "'" + MANIFEST + "' is empty." );
      }
      return manifest;
   }

   private static void validateIdentity( final PluginManifest manifest, final List<String> problems ) {
      if ( manifest.id() == null || !ID.matcher( manifest.id() ).matches() ) {
         problems.add( "'id' must consist of 2 to 63 lowercase letters, digits and hyphens, e.g. 'example-import'" );
      }
      if ( manifest.name() == null || manifest.name().isBlank() ) {
         problems.add( "'name' is missing" );
      }
      if ( manifest.version() == null || !VERSION.matcher( manifest.version() ).matches() ) {
         problems.add( "'version' must start with a letter or digit and consist of letters, digits, '.', '+', '_' and '-', e.g. '1.0.0'" );
      }
      if ( manifest.pluginApiVersion() == null ) {
         problems.add( "'pluginApiVersion' is missing" );
      } else if ( manifest.pluginApiVersion() != PluginApi.VERSION ) {
         problems.add( "the plugin was built for plugin API " + manifest.pluginApiVersion() + ", but this editor supports version "
               + PluginApi.VERSION );
      }
   }

   private static void validateFrontend( final Path pluginDirectory, final Frontend frontend, final List<String> problems ) {
      if ( frontend == null ) {
         return;
      }
      if ( frontend.entry() == null || !isFrontendFile( pluginDirectory, frontend.entry() ) ) {
         problems.add( "'frontend.entry' must name a JavaScript file below '" + FRONTEND + "/' of the archive, e.g. 'frontend/main.js'" );
      }
      if ( frontend.element() == null || !CUSTOM_ELEMENT.matcher( frontend.element() ).matches() ) {
         problems.add( "'frontend.element' must be a valid custom element name containing a hyphen, e.g. 'ame-plugin-example-import'" );
      }
   }

   private static boolean isFrontendFile( final Path pluginDirectory, final String entry ) {
      final Path frontendDirectory = pluginDirectory.resolve( FRONTEND ).normalize();
      final Path file = pluginDirectory.resolve( entry ).normalize();
      return !Path.of( entry ).isAbsolute() && file.startsWith( frontendDirectory ) && entry.endsWith( ".js" ) && Files.isRegularFile( file );
   }

   private static void validateCommands( final PluginManifest manifest, final List<String> problems ) {
      if ( !manifest.commands().isEmpty() && manifest.frontend() == null ) {
         problems.add( "commands need a 'frontend' to open" );
      }
      final Set<String> ids = new HashSet<>();
      for ( final CommandContribution command : manifest.commands() ) {
         if ( command.id() == null || !CONTRIBUTION_ID.matcher( command.id() ).matches() ) {
            problems.add( "the 'id' of each command must consist of up to 64 letters, digits, '.', '_' and '-'" );
         } else if ( !ids.add( command.id() ) ) {
            problems.add( "the command id '" + command.id() + "' is used more than once" );
         }
         validateCommand( command, problems );
      }
   }

   private static void validateCommand( final CommandContribution command, final List<String> problems ) {
      final String name = "the command '" + command.id() + "'";
      if ( !CommandContribution.KINDS.contains( command.kind() ) ) {
         problems.add( "the 'kind' of " + name + " must be one of " + String.join( ", ", CommandContribution.KINDS ) );
      }
      if ( command.label() == null || command.label().isBlank() ) {
         problems.add( name + " has no 'label'" );
      }
      if ( !ICON.matcher( command.icon() ).matches() ) {
         problems.add( "the 'icon' of " + name + " must be the name of a Material icon, e.g. 'upload_file'" );
      }
      if ( !CommandContribution.PLACEMENTS.containsAll( command.placements() ) ) {
         problems.add( "the 'placements' of " + name + " must be any of " + String.join( ", ", CommandContribution.PLACEMENTS ) );
      } else if ( command.placements().size() != Set.copyOf( command.placements() ).size() ) {
         problems.add( "the 'placements' of " + name + " contain a placement more than once" );
      }
      if ( !CommandContribution.MENUS.contains( command.menu() ) ) {
         problems.add( "the 'menu' of " + name + " must be one of " + String.join( ", ", CommandContribution.MENUS ) );
      }
   }
}
