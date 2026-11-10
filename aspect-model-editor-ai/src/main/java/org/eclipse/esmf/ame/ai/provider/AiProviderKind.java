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
import java.util.Optional;

import org.eclipse.esmf.ame.ai.model.provider.AiProviderInfo;

import io.micronaut.core.annotation.Nullable;

/**
 * The supported AI providers.
 */
public enum AiProviderKind {
   COPILOT( "copilot", "GitHub Copilot", false, false, null, false, null ),
   OPENAI( "openai", "OpenAI", true, false, "https://api.openai.com/v1", false, "gpt-4.1" ),
   ANTHROPIC( "anthropic", "Anthropic", true, false, "https://api.anthropic.com/v1", false, "claude-sonnet-4-5" ),
   AZURE_OPENAI( "azure-openai", "Azure OpenAI", true, true, null, false, null ),
   OLLAMA( "ollama", "Ollama", false, false, "http://localhost:11434/v1", true, null ),
   LMSTUDIO( "lmstudio", "LM Studio", false, false, "http://localhost:1234/v1", true, null ),
   OPENAI_COMPATIBLE( "openai-compatible", "OpenAI-compatible", false, true, null, false, null );

   private final String id;
   private final String label;
   private final boolean requiresKey;
   private final boolean requiresBaseUrl;
   private final @Nullable String defaultBaseUrl;
   private final boolean local;
   private final @Nullable String defaultModel;

   AiProviderKind( final String id, final String label, final boolean requiresKey, final boolean requiresBaseUrl,
         final @Nullable String defaultBaseUrl, final boolean local, final @Nullable String defaultModel ) {
      this.id = id;
      this.label = label;
      this.requiresKey = requiresKey;
      this.requiresBaseUrl = requiresBaseUrl;
      this.defaultBaseUrl = defaultBaseUrl;
      this.local = local;
      this.defaultModel = defaultModel;
   }

   /**
    * @param id the id, e.g. {@code openai}
    * @return the provider with the id
    */
   public static Optional<AiProviderKind> fromId( final @Nullable String id ) {
      return Arrays.stream( values() ).filter( kind -> kind.id.equals( id ) ).findFirst();
   }

   /**
    * @return the id used in requests
    */
   public String id() {
      return id;
   }

   /**
    * @return whether an API key is required
    */
   public boolean requiresKey() {
      return requiresKey;
   }

   /**
    * @return whether a base URL is required
    */
   public boolean requiresBaseUrl() {
      return requiresBaseUrl;
   }

   /**
    * @return the base URL used when none is configured
    */
   public @Nullable String defaultBaseUrl() {
      return defaultBaseUrl;
   }

   /**
    * Whether the provider runs on this machine, i.e. no model content leaves it.
    */
   public boolean isLocal() {
      return local;
   }

   /**
    * @return the model used when none is configured
    */
   public @Nullable String defaultModel() {
      return defaultModel;
   }

   /**
    * @param available whether the provider can be used
    * @return the description of the provider sent to the client
    */
   public AiProviderInfo toInfo( final boolean available ) {
      return new AiProviderInfo( id, label, requiresKey, requiresBaseUrl, defaultBaseUrl, local, available, defaultModel );
   }
}
