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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderConfig;
import org.eclipse.esmf.ame.ai.model.provider.AiTestResult;

import org.junit.jupiter.api.Test;

class AiProviderServiceTest {
   private static final String KEY = "sk-secret-0815";
   private static final AiProviderConfig OLLAMA = new AiProviderConfig( "ollama", "llama3.2", null, null );
   private static final AiProviderConfig OPENAI = new AiProviderConfig( "openai", "gpt-4.1", null, null );
   private static final List<AiModelInfo> MODELS = List.of( AiModelInfo.of( "llama3.2", null ), AiModelInfo.of( "phi-4", "Phi 4" ) );

   private static StubAiProvider provider( final Function<AiProviderSettings, List<AiModelInfo>> models ) {
      return new StubAiProvider( EnumSet.allOf( AiProviderKind.class ), true, models );
   }

   private static AiProviderService service( final StubAiProvider provider, final boolean localOnly ) {
      return new AiProviderService( new AiProviderFactory( List.of( provider ), localOnly ) );
   }

   private static AiProviderService service( final Function<AiProviderSettings, List<AiModelInfo>> models ) {
      return service( provider( models ), false );
   }

   @Test
   void listsTheProvidersOfTheFactory() {
      final StubAiProvider provider = provider( settings -> MODELS );

      assertEquals( new AiProviderFactory( List.of( provider ), true ).infos(), service( provider, true ).providers() );
      assertEquals( AiProviderKind.values().length, service( settings -> MODELS ).providers().size() );
   }

   @Test
   void listsTheModelsWithTheResolvedSettings() {
      final StubAiProvider provider = provider( settings -> MODELS );

      assertEquals( MODELS, service( provider, false ).models( OLLAMA, "  " ) );

      final AiProviderSettings settings = provider.requests().getFirst();
      assertEquals( AiProviderKind.OLLAMA, settings.kind() );
      assertEquals( "llama3.2", settings.model() );
      assertEquals( "http://localhost:11434/v1", settings.baseUrl() );
      assertNull( settings.apiKey() );
   }

   @Test
   void mapsProviderErrorsOfTheModelList() {
      final AiException exception = assertThrows( AiException.class, () -> service( settings -> {
         throw new RuntimeException( "wrapped", new ConnectException( "refused" ) );
      } ).models( OLLAMA, null ) );

      assertEquals( AiErrorCode.OFFLINE, exception.getCode() );
   }

   @Test
   void rejectsAMissingConfiguration() {
      assertThrows( IllegalArgumentException.class, () -> service( settings -> MODELS ).models( null, null ) );
   }

   @Test
   void testSucceedsWithTheModels() {
      final AiTestResult result = service( settings -> MODELS ).test( OPENAI, KEY );

      assertTrue( result.ok() );
      assertEquals( MODELS, result.models() );
      assertNull( result.code() );
   }

   @Test
   void testReportsAMissingKey() {
      final AiTestResult result = service( settings -> MODELS ).test( OPENAI, null );

      assertFalse( result.ok() );
      assertEquals( "invalid_key", result.code() );
      assertNull( result.models() );
   }

   @Test
   void testReportsAnInvalidConfiguration() {
      final AiTestResult missing = service( settings -> MODELS ).test( null, KEY );
      final AiTestResult unknown = service( settings -> MODELS ).test( new AiProviderConfig( "unknown", null, null, null ), KEY );
      final AiTestResult badUrl = service( settings -> MODELS ).test( new AiProviderConfig( "openai-compatible", null, "ftp://x", null ),
            KEY );

      assertEquals( AiTestResult.failure( "provider_error", "The AI provider is missing." ), missing );
      assertEquals( AiTestResult.failure( "provider_error", "Unknown AI provider: unknown" ), unknown );
      assertEquals( "provider_error", badUrl.code() );
   }

   @Test
   void testReportsTheLocalOnlyMode() {
      final AiTestResult result = service( provider( settings -> MODELS ), true ).test( OPENAI, KEY );

      assertEquals( "local_only", result.code() );
   }

   @Test
   void testReportsMappedProviderErrorsWithoutTheKey() {
      final AiTestResult result = service( settings -> {
         throw new IllegalStateException( "Bad request with key " + KEY );
      } ).test( OPENAI, KEY );

      assertFalse( result.ok() );
      assertEquals( "provider_error", result.code() );
      assertEquals( "Request to openai failed: Bad request with key ***", result.message() );
   }

   @Test
   void testReportsACancellationAsProviderError() {
      final AiTestResult result = service( settings -> {
         throw new CancellationException( "cancelled" );
      } ).test( OLLAMA, null );

      assertEquals( AiTestResult.failure( "provider_error", "cancelled" ), result );
   }
}
