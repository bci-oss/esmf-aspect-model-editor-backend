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

import java.util.List;
import java.util.Optional;

import org.eclipse.esmf.ame.api.model.request.WorkspaceRequest;
import org.eclipse.esmf.ame.services.models.workspace.ModelSource;
import org.eclipse.esmf.ame.services.models.workspace.WorkspaceConfig;
import org.eclipse.esmf.ame.services.workspace.WorkspaceConfigService;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;

/**
 * Controller for managing aspect model workspaces.
 */
@Controller( "workspaces" )
@Produces( MediaType.APPLICATION_JSON )
public class WorkspaceController {
   private final WorkspaceConfigService workspaceConfigService;

   public WorkspaceController( final WorkspaceConfigService workspaceConfigService ) {
      this.workspaceConfigService = workspaceConfigService;
   }

   /**
    * Retrieves current workspace configuration including all known workspaces and the active workspace.
    *
    * @return HttpResponse containing the workspace configuration
    */
   @Get
   public HttpResponse<WorkspaceConfig> getWorkspaces() {
      return HttpResponse.ok( workspaceConfigService.getConfig() );
   }

   /**
    * Adds a new workspace folder to the configuration.
    *
    * @param request the workspace request containing the path
    * @return HttpResponse containing the updated workspace configuration
    */
   @Post
   public HttpResponse<WorkspaceConfig> addWorkspace( @Body final WorkspaceRequest request ) {
      if ( request == null || request.path() == null || request.path().isBlank() ) {
         return HttpResponse.badRequest();
      }
      return HttpResponse.ok( workspaceConfigService.addWorkspace( request.path().trim() ) );
   }

   /**
    * Removes a workspace folder from the configuration.
    *
    * @param path query param representing the path to remove
    * @param body optional request body representing the path to remove
    * @return HttpResponse containing the updated workspace configuration
    */
   @Delete
   public HttpResponse<WorkspaceConfig> deleteWorkspace( @QueryValue( "path" ) final Optional<String> path,
         @Nullable @Body final WorkspaceRequest body ) {
      final String targetPath = path.or( () -> Optional.ofNullable( body ).map( WorkspaceRequest::path ) ).orElse( null );
      if ( targetPath == null || targetPath.isBlank() ) {
         return HttpResponse.badRequest();
      }
      return HttpResponse.ok( workspaceConfigService.removeWorkspace( targetPath.trim() ) );
   }

   /**
    * Lists all sources models are read from, in resolution order: the workspaces followed by the synchronized GitHub
    * sources.
    *
    * @return HttpResponse containing the model sources
    */
   @Get( "/sources" )
   public HttpResponse<List<ModelSource>> getSources() {
      return HttpResponse.ok( workspaceConfigService.getSources() );
   }

   /**
    * Resets the workspace configuration to the default single workspace.
    *
    * @return HttpResponse containing the reset workspace configuration
    */
   @Post( "/reset" )
   public HttpResponse<WorkspaceConfig> resetWorkspaces() {
      return HttpResponse.ok( workspaceConfigService.resetToDefault() );
   }
}
