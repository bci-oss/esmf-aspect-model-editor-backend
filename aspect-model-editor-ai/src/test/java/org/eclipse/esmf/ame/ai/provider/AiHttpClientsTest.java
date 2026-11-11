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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Duration;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import org.junit.jupiter.api.Test;

class AiHttpClientsTest {
   @Test
   void jdkClientsFollowRedirectsWithTheGivenConnectTimeout() {
      final HttpClient client = AiHttpClients.jdkClientBuilder( Duration.ofSeconds( 3 ), false ).build();

      assertEquals( Duration.ofSeconds( 3 ), client.connectTimeout().orElseThrow() );
      assertEquals( HttpClient.Redirect.NORMAL, client.followRedirects() );
   }

   @Test
   void localClientsUseNoProxy() {
      final HttpClient client = AiHttpClients.jdkClientBuilder( AiHttpClients.CONNECT_TIMEOUT, false ).build();

      assertTrue( client.proxy().isEmpty() );
   }

   @Test
   void langChainClientsUseTheDefaultTimeouts() {
      final JdkHttpClientBuilder builder = AiHttpClients.langChainClientBuilder( false );

      assertEquals( AiHttpClients.CONNECT_TIMEOUT, builder.connectTimeout() );
      assertEquals( AiHttpClients.READ_TIMEOUT, builder.readTimeout() );
      assertEquals( AiHttpClients.CONNECT_TIMEOUT, builder.httpClientBuilder().build().connectTimeout().orElseThrow() );
   }
}
