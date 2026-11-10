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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.esmf.ame.ai.model.provider.AiLocalServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiLocalServerProbeTest {
   private static final String MODELS = "/v1/models";

   private MockAiServer ollama;
   private MockAiServer lmStudio;

   @BeforeEach
   void setUp() throws Exception {
      ollama = new MockAiServer();
      lmStudio = new MockAiServer();
   }

   @AfterEach
   void tearDown() {
      ollama.close();
      lmStudio.close();
   }

   private AiLocalServerProbe probe() {
      final Map<AiProviderKind, String> servers = new LinkedHashMap<>();
      // inserted in reverse order to check that the result is ordered by kind
      servers.put( AiProviderKind.LMSTUDIO, lmStudio.baseUrl() );
      servers.put( AiProviderKind.OLLAMA, ollama.baseUrl() );
      return new AiLocalServerProbe( servers );
   }

   @Test
   void detectsAnsweringServersWithTheirModelsOrderedByKind() {
      ollama.enqueue( MODELS, MockAiServer.Response.json( 200, """
            {"object":"list","data":[{"id":"llama3.2"},{"id":"qwen2.5-coder"},{"object":"model"}]}""" ) );
      lmStudio.enqueue( MODELS, MockAiServer.Response.json( 200, """
            {"data":[{"id":"phi-4"}]}""" ) );

      final List<AiLocalServer> servers = probe().probe();

      assertEquals( List.of( new AiLocalServer( "ollama", ollama.baseUrl(), List.of( "llama3.2", "qwen2.5-coder" ) ),
            new AiLocalServer( "lmstudio", lmStudio.baseUrl(), List.of( "phi-4" ) ) ), servers );
      assertEquals( "GET", ollama.requests().getFirst().method() );
      assertEquals( MODELS, ollama.requests().getFirst().path() );
   }

   @Test
   void ignoresServersAnsweringWithAnError() {
      ollama.enqueue( MODELS, MockAiServer.Response.json( 500, "{}" ) );
      lmStudio.enqueue( MODELS, MockAiServer.Response.json( 200, "{\"data\":[{\"id\":\"phi-4\"}]}" ) );

      final List<AiLocalServer> servers = probe().probe();

      assertEquals( 1, servers.size() );
      assertEquals( "lmstudio", servers.getFirst().kind() );
   }

   @Test
   void ignoresServersThatCannotBeReached() {
      lmStudio.close();
      ollama.enqueue( MODELS, MockAiServer.Response.json( 200, "{\"data\":[]}" ) );

      final List<AiLocalServer> servers = probe().probe();

      assertEquals( List.of( new AiLocalServer( "ollama", ollama.baseUrl(), List.of() ) ), servers );
   }

   @Test
   void treatsAnAnswerWithoutModelListAsServerWithoutModels() {
      ollama.enqueue( MODELS, MockAiServer.Response.json( 200, "not json" ) );
      lmStudio.enqueue( MODELS, MockAiServer.Response.json( 200, "{\"models\":[\"x\"]}" ) );

      final List<AiLocalServer> servers = probe().probe();

      assertEquals( 2, servers.size() );
      assertTrue( servers.stream().allMatch( server -> server.models().isEmpty() ) );
   }

   @Test
   void probesNothingWithoutServers() {
      assertTrue( new AiLocalServerProbe( Map.of() ).probe().isEmpty() );
   }
}
