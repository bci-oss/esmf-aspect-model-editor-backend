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

import org.eclipse.esmf.ame.constants.ApplicationConstants;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceConfig;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceStatus;
import org.eclipse.esmf.ame.services.workspace.GithubSourceService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceConfigService;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/**
 * Manages the read-only GitHub sources. Checking and synchronizing accept an optional access token in the header
 * {@value ApplicationConstants.Headers#GITHUB_TOKEN}; the token is used for this request only.
 */
@Controller( "sources/github" )
@Produces( MediaType.APPLICATION_JSON )
@ExecuteOn( TaskExecutors.BLOCKING )
public class GithubSourceController {
   private final WorkspaceConfigService workspaceConfigService;
   private final GithubSourceService githubSourceService;

   public GithubSourceController( final WorkspaceConfigService workspaceConfigService, final GithubSourceService githubSourceService ) {
      this.workspaceConfigService = workspaceConfigService;
      this.githubSourceService = githubSourceService;
   }

   /**
    * Lists all GitHub sources with the state of their local copies.
    */
   @Get
   public HttpResponse<List<GithubSourceStatus>> getSources() {
      return HttpResponse.ok( githubSourceService.getStatuses() );
   }

   /**
    * Adds a GitHub source. Its models are available after the first synchronization.
    */
   @Post
   public HttpResponse<GithubSourceStatus> addSource( @Body final GithubSourceConfig request ) {
      final GithubSourceConfig added = workspaceConfigService.addGithubSource( request ).githubSources().getLast();
      return HttpResponse.created( githubSourceService.getStatus( added.id() ) );
   }

   /**
    * Updates a GitHub source. Changing repository, branch, tag or directory discards the local copy.
    */
   @Put( "/{id}" )
   public HttpResponse<GithubSourceStatus> updateSource( @PathVariable final String id, @Body final GithubSourceConfig request ) {
      final List<GithubSourceConfig> before = workspaceConfigService.getConfig().githubSources();
      final int index = before.indexOf( workspaceConfigService.getGithubSource( id ) );
      final GithubSourceConfig updated = workspaceConfigService.updateGithubSource( id, request ).githubSources().get( index );
      if ( !updated.id().equals( id ) ) {
         githubSourceService.forget( id );
      }
      return HttpResponse.ok( githubSourceService.getStatus( updated.id() ) );
   }

   /**
    * Removes a GitHub source and its local copy.
    */
   @Delete( "/{id}" )
   public HttpResponse<List<GithubSourceStatus>> removeSource( @PathVariable final String id ) {
      workspaceConfigService.removeGithubSource( id );
      githubSourceService.forget( id );
      return HttpResponse.ok( githubSourceService.getStatuses() );
   }

   /**
    * Checks whether the branch or tag of a source points to a newer commit than the local copy. Nothing is downloaded.
    */
   @Post( "/{id}/check" )
   public HttpResponse<GithubSourceStatus> checkSource( @PathVariable final String id,
         @Nullable @Header( ApplicationConstants.Headers.GITHUB_TOKEN ) final String token ) {
      return HttpResponse.ok( githubSourceService.check( id, token ) );
   }

   /**
    * Downloads the current state of the branch or tag and replaces the local copy.
    */
   @Post( "/{id}/sync" )
   public HttpResponse<GithubSourceStatus> syncSource( @PathVariable final String id,
         @Nullable @Header( ApplicationConstants.Headers.GITHUB_TOKEN ) final String token ) {
      return HttpResponse.ok( githubSourceService.sync( id, token ) );
   }
}
