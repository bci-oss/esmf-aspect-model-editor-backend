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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.esmf.ame.services.models.workspace.GithubSourceConfig;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceStatus;
import org.eclipse.esmf.ame.services.models.workspace.WorkspaceConfig;
import org.eclipse.esmf.ame.services.workspace.GithubSourceService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceConfigService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GithubSourceControllerTest {
   private static final GithubSourceConfig SOURCE = new GithubSourceConfig( "owner_repo_main", null, "owner", "repo", null, "main",
         null, null );
   private static final GithubSourceStatus STATUS = new GithubSourceStatus( SOURCE, "/github/owner_repo_main", false, null, null,
         null, null, false, null );

   private WorkspaceConfigService configService;
   private GithubSourceService githubSourceService;
   private GithubSourceController controller;

   @BeforeEach
   void setUp() {
      configService = mock( WorkspaceConfigService.class );
      githubSourceService = mock( GithubSourceService.class );
      controller = new GithubSourceController( configService, githubSourceService );
   }

   private static WorkspaceConfig configWith( final GithubSourceConfig... sources ) {
      return new WorkspaceConfig( 2, List.of( "/models" ), List.of( sources ) );
   }

   @Test
   void addsASource() {
      when( configService.addGithubSource( SOURCE ) ).thenReturn( configWith( SOURCE ) );
      when( githubSourceService.getStatus( SOURCE.id() ) ).thenReturn( STATUS );

      final HttpResponse<GithubSourceStatus> response = controller.addSource( SOURCE );

      assertEquals( HttpStatus.CREATED, response.getStatus() );
      assertEquals( STATUS, response.body() );
   }

   @Test
   void updatingTheRefForgetsTheOldCheckResult() {
      final GithubSourceConfig changed = new GithubSourceConfig( "owner_repo_dev", null, "owner", "repo", null, "dev", null, null );
      when( configService.getConfig() ).thenReturn( configWith( SOURCE ) );
      when( configService.getGithubSource( SOURCE.id() ) ).thenReturn( SOURCE );
      when( configService.updateGithubSource( SOURCE.id(), changed ) ).thenReturn( configWith( changed ) );
      when( githubSourceService.getStatus( changed.id() ) ).thenReturn( STATUS );

      assertEquals( HttpStatus.OK, controller.updateSource( SOURCE.id(), changed ).getStatus() );
      verify( githubSourceService ).forget( SOURCE.id() );
   }

   @Test
   void removesASource() {
      when( githubSourceService.getStatuses() ).thenReturn( List.of() );

      assertEquals( List.of(), controller.removeSource( SOURCE.id() ).body() );
      verify( configService ).removeGithubSource( SOURCE.id() );
      verify( githubSourceService ).forget( SOURCE.id() );
   }

   @Test
   void passesTheTokenToCheckAndSync() {
      when( githubSourceService.check( SOURCE.id(), "token" ) ).thenReturn( STATUS );
      when( githubSourceService.sync( SOURCE.id(), null ) ).thenReturn( STATUS );

      assertEquals( STATUS, controller.checkSource( SOURCE.id(), "token" ).body() );
      assertEquals( STATUS, controller.syncSource( SOURCE.id(), null ).body() );
      verify( githubSourceService, never() ).sync( SOURCE.id(), "token" );
   }
}
