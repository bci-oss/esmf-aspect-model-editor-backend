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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderInfo;

import org.junit.jupiter.api.Test;

class AiProviderFactoryTest {
   private final StubAiProvider openAi = new StubAiProvider( AiProviderKind.OPENAI, AiProviderKind.OLLAMA,
         AiProviderKind.OPENAI_COMPATIBLE );
   private final StubAiProvider anthropic = new StubAiProvider( AiProviderKind.ANTHROPIC );
   private final StubAiProvider copilot = new StubAiProvider( EnumSet.of( AiProviderKind.COPILOT ), false, settings -> List.of() );

   private static AiProviderSettings settings( final AiProviderKind kind, final String baseUrl ) {
      return new AiProviderSettings( kind, null, baseUrl, null, "sk-test" );
   }

   private AiProviderFactory factory( final boolean localOnly ) {
      return new AiProviderFactory( List.of( openAi, anthropic, copilot ), localOnly );
   }

   @Test
   void selectsTheProviderOfTheKind() {
      final AiProviderFactory factory = factory( false );

      assertFalse( factory.isLocalOnly() );
      assertSame( openAi, factory.get( settings( AiProviderKind.OPENAI, AiProviderKind.OPENAI.defaultBaseUrl() ) ) );
      assertSame( openAi, factory.get( settings( AiProviderKind.OLLAMA, AiProviderKind.OLLAMA.defaultBaseUrl() ) ) );
      assertSame( anthropic, factory.get( settings( AiProviderKind.ANTHROPIC, AiProviderKind.ANTHROPIC.defaultBaseUrl() ) ) );
   }

   @Test
   void rejectsKindsWithoutProvider() {
      final AiException exception = assertThrows( AiException.class,
            () -> factory( false ).get( settings( AiProviderKind.LMSTUDIO, AiProviderKind.LMSTUDIO.defaultBaseUrl() ) ) );

      assertEquals( AiErrorCode.PROVIDER_ERROR, exception.getCode() );
      assertEquals( "The AI provider lmstudio is not available.", exception.getMessage() );
   }

   @Test
   void rejectsUnavailableProviders() {
      final AiException exception = assertThrows( AiException.class,
            () -> factory( false ).get( settings( AiProviderKind.COPILOT, null ) ) );

      assertEquals( AiErrorCode.PROVIDER_ERROR, exception.getCode() );
   }

   @Test
   void allowsOnlyLocalProvidersInLocalOnlyMode() {
      final AiProviderFactory factory = factory( true );

      assertTrue( factory.isLocalOnly() );
      assertEquals( AiErrorCode.LOCAL_ONLY, assertThrows( AiException.class,
            () -> factory.get( settings( AiProviderKind.OPENAI, AiProviderKind.OPENAI.defaultBaseUrl() ) ) ).getCode() );
      assertEquals( AiErrorCode.LOCAL_ONLY, assertThrows( AiException.class,
            () -> factory.get( settings( AiProviderKind.OPENAI_COMPATIBLE, "https://ai.example.com/v1" ) ) ).getCode() );
      assertSame( openAi, factory.get( settings( AiProviderKind.OLLAMA, AiProviderKind.OLLAMA.defaultBaseUrl() ) ) );
      assertSame( openAi, factory.get( settings( AiProviderKind.OPENAI_COMPATIBLE, "http://127.0.0.1:8080/v1" ) ) );
   }

   @Test
   void listsAllKindsWithTheirAvailability() {
      final List<AiProviderInfo> infos = factory( false ).infos();

      assertEquals( List.of( "copilot", "openai", "anthropic", "azure-openai", "ollama", "lmstudio", "openai-compatible" ),
            infos.stream().map( AiProviderInfo::kind ).toList() );
      assertEquals( List.of( false, true, true, false, true, false, true ),
            infos.stream().map( AiProviderInfo::available ).toList() );
   }

   @Test
   void listsOnlyLocalAndOpenAiCompatibleKindsInLocalOnlyMode() {
      final List<AiProviderInfo> infos = factory( true ).infos();

      assertEquals( List.of( "ollama", "lmstudio", "openai-compatible" ), infos.stream().map( AiProviderInfo::kind ).toList() );
   }

   @Test
   void laterProvidersOverrideEarlierOnesForTheSameKind() {
      final StubAiProvider override = new StubAiProvider( AiProviderKind.OPENAI );
      final AiProviderFactory factory = new AiProviderFactory( List.of( openAi, override ), false );

      assertSame( override, factory.get( settings( AiProviderKind.OPENAI, AiProviderKind.OPENAI.defaultBaseUrl() ) ) );
   }
}
