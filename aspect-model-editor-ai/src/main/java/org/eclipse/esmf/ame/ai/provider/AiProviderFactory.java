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

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderInfo;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

/**
 * Selects the {@link AiProvider} for a provider kind and enforces the local-only mode ({@code ame.ai.local-only}), in
 * which no model content may leave the machine.
 */
@Singleton
public class AiProviderFactory {
   private final Map<AiProviderKind, AiProvider> providers = new EnumMap<>( AiProviderKind.class );
   private final boolean localOnly;

   public AiProviderFactory( final List<AiProvider> providers, @Value( "${ame.ai.local-only:false}" ) final boolean localOnly ) {
      providers.forEach( provider -> provider.kinds().forEach( kind -> this.providers.put( kind, provider ) ) );
      this.localOnly = localOnly;
   }

   /**
    * @return whether only local providers are allowed ({@code ame.ai.local-only})
    */
   public boolean isLocalOnly() {
      return localOnly;
   }

   /**
    * @return the provider of the settings
    * @throws AiException with {@code local_only} if the provider is not local and only local providers are allowed
    */
   public AiProvider get( final AiProviderSettings settings ) {
      if ( localOnly && !settings.isLocal() ) {
         throw new AiException( AiErrorCode.LOCAL_ONLY, "Only local AI providers are allowed in this installation." );
      }
      final AiProvider provider = providers.get( settings.kind() );
      if ( provider == null || !provider.isAvailable() ) {
         throw new AiException( AiErrorCode.PROVIDER_ERROR, "The AI provider " + settings.kind().id() + " is not available." );
      }
      return provider;
   }

   /**
    * @return the providers shown to the user; in local-only mode only the local ones and the OpenAI-compatible one
    */
   public List<AiProviderInfo> infos() {
      return Arrays.stream( AiProviderKind.values() )
            .filter( kind -> !localOnly || kind.isLocal() || kind == AiProviderKind.OPENAI_COMPATIBLE )
            .map( kind -> {
               final AiProvider provider = providers.get( kind );
               return kind.toInfo( provider != null && provider.isAvailable() );
            } )
            .toList();
   }
}
