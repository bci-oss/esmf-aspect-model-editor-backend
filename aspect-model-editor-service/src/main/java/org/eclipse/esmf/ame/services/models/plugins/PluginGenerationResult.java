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

import java.util.Base64;
import java.util.List;

import org.eclipse.esmf.ame.plugin.api.GeneratedFile;
import org.eclipse.esmf.ame.plugin.api.GenerationResult;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The files created by a generator of a plugin, as returned to its user interface.
 *
 * @param files the generated files
 * @param warnings problems the user should know about
 */
@Serdeable
@Introspected
public record PluginGenerationResult( List<File> files, List<String> warnings ) {
   public PluginGenerationResult {
      files = List.copyOf( files );
      warnings = List.copyOf( warnings );
   }

   /**
    * Copies the result returned by a plugin.
    *
    * @param result the result of the plugin
    * @return the copy
    */
   public static PluginGenerationResult of( final GenerationResult result ) {
      return new PluginGenerationResult( result.files().stream().map( File::of ).toList(), result.warnings() );
   }

   /**
    * A generated file.
    *
    * @param fileName the name the file is suggested to be saved as
    * @param mediaType the media type of the content
    * @param content the content, Base64 encoded
    */
   @Serdeable
   @Introspected
   public record File( String fileName, String mediaType, String content ) {
      static File of( final GeneratedFile file ) {
         return new File( file.fileName(), file.mediaType(), Base64.getEncoder().encodeToString( file.content() ) );
      }
   }
}
