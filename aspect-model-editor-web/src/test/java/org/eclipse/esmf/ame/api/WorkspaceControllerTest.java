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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.eclipse.esmf.ame.api.model.request.WorkspaceRequest;
import org.eclipse.esmf.ame.services.models.workspace.ModelSource;
import org.eclipse.esmf.ame.services.models.workspace.WorkspaceConfig;
import org.eclipse.esmf.ame.services.workspace.WorkspaceConfigService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkspaceControllerTest {

   private WorkspaceConfigService workspaceConfigService;
   private WorkspaceController workspaceController;

   @BeforeEach
   void setUp() {
      workspaceConfigService = mock( WorkspaceConfigService.class );
      workspaceController = new WorkspaceController( workspaceConfigService );
   }

   @Test
   void testGetWorkspaces() {
      final WorkspaceConfig config = new WorkspaceConfig( List.of( "/workspace1", "/workspace2" ) );
      when( workspaceConfigService.getConfig() ).thenReturn( config );

      final HttpResponse<WorkspaceConfig> response = workspaceController.getWorkspaces();

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( config, response.body() );
      verify( workspaceConfigService ).getConfig();
   }

   @Test
   void testGetSources() {
      final List<ModelSource> sources = List.of(
            new ModelSource( ModelSource.WORKSPACE, "/models", "models (Default)", "/models", false, true ) );
      when( workspaceConfigService.getSources() ).thenReturn( sources );

      assertEquals( sources, workspaceController.getSources().body() );
   }

   @Test
   void testAddWorkspaceSuccess() {
      final WorkspaceConfig config = new WorkspaceConfig( List.of( "/workspace1", "/workspace2" ) );
      when( workspaceConfigService.addWorkspace( "/workspace2" ) ).thenReturn( config );

      final WorkspaceRequest request = new WorkspaceRequest( "/workspace2" );
      final HttpResponse<WorkspaceConfig> response = workspaceController.addWorkspace( request );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( config, response.body() );
      verify( workspaceConfigService ).addWorkspace( "/workspace2" );
   }

   @Test
   void testAddWorkspaceBadRequestWhenNullOrBlank() {
      assertEquals( HttpStatus.BAD_REQUEST, workspaceController.addWorkspace( null ).getStatus() );
      assertEquals( HttpStatus.BAD_REQUEST, workspaceController.addWorkspace( new WorkspaceRequest( null ) ).getStatus() );
      assertEquals( HttpStatus.BAD_REQUEST, workspaceController.addWorkspace( new WorkspaceRequest( "   " ) ).getStatus() );
   }

   @Test
   void testDeleteWorkspaceByQueryParam() {
      final WorkspaceConfig config = new WorkspaceConfig( List.of( "/workspace1" ) );
      when( workspaceConfigService.removeWorkspace( "/workspace2" ) ).thenReturn( config );

      final HttpResponse<WorkspaceConfig> response = workspaceController.deleteWorkspace( Optional.of( "/workspace2" ), null );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( config, response.body() );
      verify( workspaceConfigService ).removeWorkspace( "/workspace2" );
   }

   @Test
   void testDeleteWorkspaceByBody() {
      final WorkspaceConfig config = new WorkspaceConfig( List.of( "/workspace1" ) );
      when( workspaceConfigService.removeWorkspace( "/workspace2" ) ).thenReturn( config );

      final HttpResponse<WorkspaceConfig> response = workspaceController.deleteWorkspace( Optional.empty(), new WorkspaceRequest( "/workspace2" ) );

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( config, response.body() );
      verify( workspaceConfigService ).removeWorkspace( "/workspace2" );
   }

   @Test
   void testDeleteWorkspaceBadRequestWhenMissing() {
      assertEquals( HttpStatus.BAD_REQUEST, workspaceController.deleteWorkspace( Optional.empty(), null ).getStatus() );
      assertEquals( HttpStatus.BAD_REQUEST, workspaceController.deleteWorkspace( Optional.of( "  " ), null ).getStatus() );
   }

   @Test
   void testResetWorkspaces() {
      final WorkspaceConfig config = new WorkspaceConfig( List.of( "/default" ) );
      when( workspaceConfigService.resetToDefault() ).thenReturn( config );

      final HttpResponse<WorkspaceConfig> response = workspaceController.resetWorkspaces();

      assertEquals( HttpStatus.OK, response.getStatus() );
      assertEquals( config, response.body() );
      verify( workspaceConfigService ).resetToDefault();
   }
}
