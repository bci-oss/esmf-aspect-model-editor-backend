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
package org.eclipse.esmf.ame.ai.provider;

import java.net.http.HttpClient;
import java.time.Duration;

import org.eclipse.esmf.ame.services.workspace.GithubSourceService;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;

/**
 * Creates the HTTP clients for the AI providers. They use the proxy configured in the environment.
 */
public final class AiHttpClients {
   public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds( 10 );
   public static final Duration READ_TIMEOUT = Duration.ofSeconds( 120 );

   private AiHttpClients() {
   }

   /**
    * @param connectTimeout the connect timeout
    * @param useProxy whether to use the proxy of the environment; not for servers on localhost
    */
   public static HttpClient.Builder jdkClientBuilder( final Duration connectTimeout, final boolean useProxy ) {
      final HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout( connectTimeout )
            .followRedirects( HttpClient.Redirect.NORMAL );
      if ( useProxy ) {
         GithubSourceService.EnvironmentProxySelector.fromEnvironment().ifPresent( builder::proxy );
      }
      return builder;
   }

   /**
    * @param useProxy whether to use the proxy of the environment; not for servers on localhost
    * @return an HTTP client builder for LangChain4j with the default timeouts
    */
   public static JdkHttpClientBuilder langChainClientBuilder( final boolean useProxy ) {
      return new JdkHttpClientBuilder()
            .httpClientBuilder( jdkClientBuilder( CONNECT_TIMEOUT, useProxy ) )
            .connectTimeout( CONNECT_TIMEOUT )
            .readTimeout( READ_TIMEOUT );
   }
}
