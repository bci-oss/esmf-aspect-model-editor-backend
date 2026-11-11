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

import org.eclipse.esmf.ame.ai.model.provider.AiLocalServer;
import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderInfo;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderRequest;
import org.eclipse.esmf.ame.ai.model.provider.AiTestResult;
import org.eclipse.esmf.ame.ai.provider.AiLocalServerProbe;
import org.eclipse.esmf.ame.ai.provider.AiProviderService;
import org.eclipse.esmf.ame.constants.ApplicationConstants;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/**
 * The AI providers of the assistant: the supported providers, the local AI servers, the models of a provider and the test of a
 * provider configuration. The API key of a provider is sent per request in the header {@code X-Ame-Ai-Key}; it is never stored or
 * logged.
 */
@Controller( "ai" )
@Produces( MediaType.APPLICATION_JSON )
@ExecuteOn( TaskExecutors.BLOCKING )
public class AiProviderController {
   private final AiProviderService providerService;
   private final AiLocalServerProbe localServerProbe;

   public AiProviderController( final AiProviderService providerService, final AiLocalServerProbe localServerProbe ) {
      this.providerService = providerService;
      this.localServerProbe = localServerProbe;
   }

   /**
    * @return the supported providers
    */
   @Get( "/providers" )
   public HttpResponse<List<AiProviderInfo>> getProviders() {
      return HttpResponse.ok( providerService.providers() );
   }

   /**
    * @return the local AI servers found on this machine
    */
   @Get( "/local" )
   public HttpResponse<List<AiLocalServer>> getLocalServers() {
      return HttpResponse.ok( localServerProbe.probe() );
   }

   /**
    * @param request the provider configuration
    * @param apiKey the API key, if the provider requires one
    * @return the models of the provider
    */
   @Post( "/models" )
   public HttpResponse<List<AiModelInfo>> getModels( @Body final AiProviderRequest request,
         @Nullable @Header( ApplicationConstants.Headers.AI_KEY ) final String apiKey ) {
      return HttpResponse.ok( providerService.models( request.provider(), apiKey ) );
   }

   /**
    * Tests a provider configuration. Always answers with 200; a failure is reported with {@code ok: false}.
    *
    * @param request the provider configuration
    * @param apiKey the API key, if the provider requires one
    * @return the result of the test
    */
   @Post( "/test" )
   public HttpResponse<AiTestResult> testProvider( @Body final AiProviderRequest request,
         @Nullable @Header( ApplicationConstants.Headers.AI_KEY ) final String apiKey ) {
      return HttpResponse.ok( providerService.test( request.provider(), apiKey ) );
   }
}
