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

package org.eclipse.esmf.ame.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.esmf.ame.exceptions.InvalidPluginException;
import org.eclipse.esmf.ame.services.models.plugins.PluginEnabledRequest;
import org.eclipse.esmf.ame.services.models.plugins.PluginGenerationRequest;
import org.eclipse.esmf.ame.services.models.plugins.PluginGenerationResult;
import org.eclipse.esmf.ame.services.models.plugins.PluginImportResult;
import org.eclipse.esmf.ame.services.models.plugins.PluginStatus;
import org.eclipse.esmf.ame.services.models.plugins.PluginsOverview;
import org.eclipse.esmf.ame.services.plugins.PluginService;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.json.JsonMapper;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.apache.commons.io.FilenameUtils;

/**
 * Manages the plugins and gives the user interface of each plugin access to its backend part.
 * <p>
 * Installing, removing, enabling and disabling plugins takes effect after a restart of the backend; the overview tells
 * whether a restart is needed.
 * </p>
 */
@Controller( "plugins" )
@Produces( MediaType.APPLICATION_JSON )
@ExecuteOn( TaskExecutors.BLOCKING )
public class PluginController {
   private static final Argument<Map<String, String>> OPTIONS_TYPE = Argument.mapOf( String.class, String.class );

   private final PluginService pluginService;
   private final JsonMapper jsonMapper;

   public PluginController( final PluginService pluginService, final JsonMapper jsonMapper ) {
      this.pluginService = pluginService;
      this.jsonMapper = jsonMapper;
   }

   /**
    * Lists the installed plugins and whether a restart is needed for changes to take effect.
    */
   @Get
   public HttpResponse<PluginsOverview> getPlugins() {
      return HttpResponse.ok( pluginService.getOverview() );
   }

   /**
    * Installs a plugin archive (ZIP), replacing an installed version of the plugin.
    */
   @Post
   @Consumes( MediaType.MULTIPART_FORM_DATA )
   public HttpResponse<PluginStatus> installPlugin( @Part( "file" ) final CompletedFileUpload file ) {
      if ( !"zip".equalsIgnoreCase( FilenameUtils.getExtension( file.getFilename() ) ) ) {
         throw new InvalidPluginException( String.format( "The selected file '%s' is not a plugin. Plugins are ZIP archives.",
               file.getFilename() ) );
      }
      try ( final InputStream archive = file.getInputStream() ) {
         return HttpResponse.created( pluginService.install( archive ) );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not read the uploaded plugin", e );
      }
   }

   /**
    * Removes a plugin and its data.
    */
   @Delete( "/{id}" )
   public HttpResponse<PluginsOverview> uninstallPlugin( @PathVariable final String id ) {
      pluginService.uninstall( id );
      return HttpResponse.ok( pluginService.getOverview() );
   }

   /**
    * Enables or disables a plugin.
    */
   @Put( "/{id}/enabled" )
   public HttpResponse<PluginStatus> setEnabled( @PathVariable final String id, @Body final PluginEnabledRequest request ) {
      return HttpResponse.ok( pluginService.setEnabled( id, request.enabled() ) );
   }

   /**
    * Calls an action of a running plugin. Payload and result are JSON defined by the plugin.
    */
   @Post( "/{id}/actions/{action}" )
   @Consumes( MediaType.APPLICATION_JSON )
   public HttpResponse<String> runAction( @PathVariable final String id, @PathVariable final String action,
         @Nullable @Body final String payload ) {
      final String result = pluginService.runAction( id, action, payload == null || payload.isBlank() ? "null" : payload );
      return HttpResponse.ok( result ).contentType( MediaType.APPLICATION_JSON_TYPE );
   }

   /**
    * Converts a file into Aspect Models with an importer of a running plugin. The optional part {@code options} is a
    * JSON object of strings passed to the importer.
    */
   @Post( "/{id}/importers/{importer}" )
   @Consumes( MediaType.MULTIPART_FORM_DATA )
   public HttpResponse<PluginImportResult> importModel( @PathVariable final String id, @PathVariable final String importer,
         @Part( "file" ) final CompletedFileUpload file, @Nullable @Part( "options" ) final String options ) {
      try {
         return HttpResponse.ok( pluginService.importModel( id, importer, file.getFilename(), file.getBytes(), parseOptions( options ) ) );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not read the uploaded file", e );
      }
   }

   /**
    * Creates files from the Aspect Model open in the editor with a generator of a running plugin. The generated files are
    * returned Base64 encoded; the user interface of the plugin decides what happens with them.
    */
   @Post( "/{id}/generators/{generator}" )
   @Consumes( MediaType.APPLICATION_JSON )
   public HttpResponse<PluginGenerationResult> generate( @PathVariable final String id, @PathVariable final String generator,
         @Body final PluginGenerationRequest request ) {
      return HttpResponse.ok( pluginService.generate( id, generator, request ) );
   }

   /**
    * Serves a file of the user interface of a running plugin, e.g. its JavaScript module.
    */
   @Get( "/{id}/frontend/{+path}" )
   @Produces( MediaType.ALL )
   public HttpResponse<SystemFile> getFrontendFile( @PathVariable final String id, @PathVariable final String path ) {
      final Path file = pluginService.frontendFile( id, path );
      final String fileName = file.getFileName().toString();
      final MediaType mediaType = fileName.endsWith( ".js" ) || fileName.endsWith( ".mjs" )
            ? MediaType.of( "text/javascript" )
            : MediaType.forFilename( fileName );
      return HttpResponse.ok( new SystemFile( file.toFile(), mediaType ) );
   }

   private Map<String, String> parseOptions( final @Nullable String options ) {
      if ( options == null || options.isBlank() ) {
         return Map.of();
      }
      try {
         final Map<String, String> parsed = jsonMapper.readValue( options, OPTIONS_TYPE );
         return parsed == null ? Map.of() : parsed;
      } catch ( final IOException | RuntimeException e ) {
         throw new IllegalArgumentException( "The import options must be a JSON object with string values.", e );
      }
   }
}
