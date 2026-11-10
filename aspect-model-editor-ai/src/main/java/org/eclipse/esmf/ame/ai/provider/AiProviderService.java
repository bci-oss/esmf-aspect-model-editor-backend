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

import java.util.List;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderConfig;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderInfo;
import org.eclipse.esmf.ame.ai.model.provider.AiTestResult;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Lists the AI providers and their models and tests provider configurations.
 */
@Singleton
public class AiProviderService {
   private final AiProviderFactory providerFactory;

   public AiProviderService( final AiProviderFactory providerFactory ) {
      this.providerFactory = providerFactory;
   }

   /**
    * @return the providers shown to the user
    */
   public List<AiProviderInfo> providers() {
      return providerFactory.infos();
   }

   /**
    * Lists the models of a provider.
    *
    * @throws AiException if the provider cannot be reached or rejects the request
    */
   public List<AiModelInfo> models( final @Nullable AiProviderConfig config, final @Nullable String apiKey ) {
      final AiProviderSettings settings = AiProviderSettings.of( config, apiKey );
      try {
         return providerFactory.get( settings ).listModels( settings );
      } catch ( final RuntimeException e ) {
         throw AiErrors.map( e, settings );
      }
   }

   /**
    * Tests a provider configuration by listing its models. Never throws.
    */
   public AiTestResult test( final @Nullable AiProviderConfig config, final @Nullable String apiKey ) {
      try {
         return AiTestResult.success( models( config, apiKey ) );
      } catch ( final AiException e ) {
         return AiTestResult.failure( e.getCode().code(), e.getMessage() );
      } catch ( final IllegalArgumentException e ) {
         return AiTestResult.failure( AiErrorCode.PROVIDER_ERROR.code(), e.getMessage() );
      } catch ( final RuntimeException e ) {
         final RuntimeException mapped = AiErrors.map( e, null );
         return AiTestResult.failure( mapped instanceof final AiException ai ? ai.getCode().code() : AiErrorCode.PROVIDER_ERROR.code(),
               mapped.getMessage() );
      }
   }
}
