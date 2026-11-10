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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;

import dev.langchain4j.model.anthropic.AnthropicModelCatalog;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import jakarta.inject.Singleton;

/**
 * The Anthropic provider.
 */
@Singleton
public class AnthropicProvider extends LangChain4jProvider {
   private static final int MAX_OUTPUT_TOKENS = 16_000;

   @Override
   public Set<AiProviderKind> kinds() {
      return EnumSet.of( AiProviderKind.ANTHROPIC );
   }

   @Override
   public List<AiModelInfo> listModels( final AiProviderSettings settings ) {
      try {
         return AnthropicModelCatalog.builder()
               .baseUrl( baseUrl( settings ) )
               .apiKey( settings.apiKey() )
               .httpClientBuilder( AiHttpClients.langChainClientBuilder( !settings.isLocal() ) )
               .timeout( AiHttpClients.CONNECT_TIMEOUT )
               .build()
               .listModels()
               .stream()
               .map( model -> AiModelInfo.of( model.name(), model.displayName() ) )
               .toList();
      } catch ( final RuntimeException exception ) {
         throw AiErrors.map( exception, settings );
      }
   }

   @Override
   protected AiProviderSettings resolveModel( final AiProviderSettings settings ) {
      return settings.model() == null ? settings.withModel( settings.kind().defaultModel() ) : settings;
   }

   @Override
   protected StreamingChatModel createStreamingModel( final AiProviderSettings settings ) {
      return AnthropicStreamingChatModel.builder()
            .baseUrl( baseUrl( settings ) )
            .apiKey( settings.apiKey() )
            .modelName( settings.model() )
            .maxTokens( MAX_OUTPUT_TOKENS )
            .httpClientBuilder( AiHttpClients.langChainClientBuilder( !settings.isLocal() ) )
            .timeout( AiHttpClients.READ_TIMEOUT )
            .build();
   }

   private static String baseUrl( final AiProviderSettings settings ) {
      return settings.baseUrl() + "/";
   }
}
