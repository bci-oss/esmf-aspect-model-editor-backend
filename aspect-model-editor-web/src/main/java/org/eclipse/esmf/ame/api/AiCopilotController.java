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

import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.copilot.CopilotGateway;
import org.eclipse.esmf.ame.ai.model.copilot.CopilotDeviceLogin;
import org.eclipse.esmf.ame.ai.model.copilot.CopilotStatus;
import org.eclipse.esmf.ame.ai.model.copilot.CopilotTokenRequest;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The login to GitHub Copilot: status, device flow and token login.
 */
@Controller( "ai/copilot" )
@Produces( MediaType.APPLICATION_JSON )
@ExecuteOn( TaskExecutors.BLOCKING )
public class AiCopilotController {
   private static final Logger LOG = LoggerFactory.getLogger( AiCopilotController.class );

   private final CopilotGateway gateway;

   public AiCopilotController( final CopilotGateway gateway ) {
      this.gateway = gateway;
   }

   /**
    * @return the login status; not logged in if the status cannot be read
    */
   @Get( "/status" )
   public HttpResponse<CopilotStatus> getStatus() {
      try {
         return HttpResponse.ok( gateway.status() );
      } catch ( final AiException e ) {
         LOG.warn( "The GitHub Copilot status could not be read: {}", e.getMessage() );
         return HttpResponse.ok( new CopilotStatus( gateway.isAvailable(), false, null, null ) );
      }
   }

   /**
    * Starts the device login: the user opens the verification URI and enters the user code.
    */
   @Post( "/login" )
   public HttpResponse<CopilotDeviceLogin> beginLogin() {
      return HttpResponse.ok( gateway.beginDeviceLogin() );
   }

   /**
    * Waits until the user has authorized the device login.
    */
   @Post( "/login/{flowId}/complete" )
   public HttpResponse<CopilotStatus> completeLogin( @PathVariable final String flowId ) {
      return HttpResponse.ok( gateway.completeDeviceLogin( flowId ) );
   }

   /**
    * Cancels a device login.
    *
    * @param flowId the id of the device login
    */
   @Delete( "/login/{flowId}" )
   public HttpResponse<Void> cancelLogin( @PathVariable final String flowId ) {
      gateway.cancelDeviceLogin( flowId );
      return HttpResponse.noContent();
   }

   /**
    * Logs in with a GitHub token.
    *
    * @param request the token
    * @return the login status
    */
   @Post( "/token" )
   public HttpResponse<CopilotStatus> loginWithToken( @Body final CopilotTokenRequest request ) {
      return HttpResponse.ok( gateway.loginWithToken( request.token() ) );
   }

   /**
    * @return the login status after the logout
    */
   @Post( "/logout" )
   public HttpResponse<CopilotStatus> logout() {
      return HttpResponse.ok( gateway.logout() );
   }
}
