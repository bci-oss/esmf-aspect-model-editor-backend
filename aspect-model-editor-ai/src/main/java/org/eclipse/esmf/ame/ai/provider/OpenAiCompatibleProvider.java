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
import java.util.Map;
import java.util.Set;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiModelCatalog;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.inject.Singleton;

/**
 * Providers with an OpenAI-compatible API: OpenAI, Azure OpenAI (v1 API), Ollama, LM Studio and any other
 * OpenAI-compatible server.
 */
@Singleton
public class OpenAiCompatibleProvider extends LangChain4jProvider {
   private static final String AZURE_KEY_HEADER = "api-key";
   private static final String AZURE_VERSION_PARAM = "api-version";

   @Override
   public Set<AiProviderKind> kinds() {
      return EnumSet.of( AiProviderKind.OPENAI, AiProviderKind.AZURE_OPENAI, AiProviderKind.OLLAMA, AiProviderKind.LMSTUDIO,
            AiProviderKind.OPENAI_COMPATIBLE );
   }

   @Override
   public List<AiModelInfo> listModels( final AiProviderSettings settings ) {
      try {
         return OpenAiModelCatalog.builder()
               .baseUrl( settings.baseUrl() )
               .apiKey( bearerKey( settings ) )
               .customHeaders( headers( settings ) )
               .customQueryParams( queryParams( settings ) )
               .httpClientBuilder( AiHttpClients.langChainClientBuilder( !settings.isLocal() ) )
               .connectTimeout( AiHttpClients.CONNECT_TIMEOUT )
               .readTimeout( AiHttpClients.CONNECT_TIMEOUT )
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
      if ( settings.model() != null ) {
         return settings;
      }
      if ( settings.kind().defaultModel() != null ) {
         return settings.withModel( settings.kind().defaultModel() );
      }
      final List<AiModelInfo> models = listModels( settings );
      if ( models.isEmpty() ) {
         throw new AiException( AiErrorCode.MODEL_NOT_FOUND, "No model is available at " + settings.kind().id() + "." );
      }
      return settings.withModel( models.getFirst().id() );
   }

   @Override
   protected StreamingChatModel createStreamingModel( final AiProviderSettings settings ) {
      return OpenAiStreamingChatModel.builder()
            .baseUrl( settings.baseUrl() )
            .apiKey( bearerKey( settings ) )
            .modelName( settings.model() )
            .customHeaders( headers( settings ) )
            .customQueryParams( queryParams( settings ) )
            .httpClientBuilder( AiHttpClients.langChainClientBuilder( !settings.isLocal() ) )
            .timeout( AiHttpClients.READ_TIMEOUT )
            .build();
   }

   private static boolean isAzure( final AiProviderSettings settings ) {
      return settings.kind() == AiProviderKind.AZURE_OPENAI;
   }

   private static String bearerKey( final AiProviderSettings settings ) {
      return isAzure( settings ) ? null : settings.apiKey();
   }

   private static Map<String, String> headers( final AiProviderSettings settings ) {
      return isAzure( settings ) && settings.apiKey() != null ? Map.of( AZURE_KEY_HEADER, settings.apiKey() ) : Map.of();
   }

   private static Map<String, String> queryParams( final AiProviderSettings settings ) {
      return isAzure( settings ) && settings.apiVersion() != null ? Map.of( AZURE_VERSION_PARAM, settings.apiVersion() ) : Map.of();
   }
}
