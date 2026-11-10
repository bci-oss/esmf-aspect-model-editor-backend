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

import java.net.URI;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.model.provider.AiProviderConfig;

import io.micronaut.core.annotation.Nullable;

/**
 * The resolved settings of a provider for one request, including the API key. Never persisted or logged:
 * {@link #toString()} masks the key.
 *
 * @param kind the provider
 * @param model the model id or {@code null} for the default of the provider
 * @param baseUrl the base URL without trailing slash or {@code null} if the provider has a fixed endpoint
 * @param apiVersion the API version (Azure OpenAI)
 * @param apiKey the API key
 */
public record AiProviderSettings(
      AiProviderKind kind,
      @Nullable String model,
      @Nullable String baseUrl,
      @Nullable String apiVersion,
      @Nullable String apiKey
) {
   /**
    * Validates the provider configuration of a request and resolves the defaults.
    *
    * @param config the configuration sent by the client
    * @param apiKey the key sent in the header {@code X-Ame-Ai-Key}
    * @return the settings
    * @throws IllegalArgumentException if the configuration is incomplete
    */
   public static AiProviderSettings of( final @Nullable AiProviderConfig config, final @Nullable String apiKey ) {
      if ( config == null ) {
         throw new IllegalArgumentException( "The AI provider is missing." );
      }
      final AiProviderKind kind = AiProviderKind.fromId( config.kind() )
            .orElseThrow( () -> new IllegalArgumentException( "Unknown AI provider: " + config.kind() ) );
      final String key = blankToNull( apiKey );
      if ( kind.requiresKey() && key == null ) {
         throw new AiException( AiErrorCode.INVALID_KEY, "An API key is required for the AI provider " + kind.id() + "." );
      }
      String baseUrl = blankToNull( config.baseUrl() );
      if ( baseUrl == null ) {
         baseUrl = kind.defaultBaseUrl();
      }
      if ( kind.requiresBaseUrl() && baseUrl == null ) {
         throw new IllegalArgumentException( "A base URL is required for the AI provider " + kind.id() + "." );
      }
      if ( baseUrl != null ) {
         baseUrl = validateBaseUrl( baseUrl );
      }
      return new AiProviderSettings( kind, blankToNull( config.model() ), baseUrl, blankToNull( config.apiVersion() ), key );
   }

   private static String validateBaseUrl( final String baseUrl ) {
      final URI uri;
      try {
         uri = URI.create( baseUrl.trim() );
      } catch ( final IllegalArgumentException exception ) {
         throw new IllegalArgumentException( "Invalid base URL of the AI provider." );
      }
      if ( !"http".equalsIgnoreCase( uri.getScheme() ) && !"https".equalsIgnoreCase( uri.getScheme() ) || uri.getHost() == null ) {
         throw new IllegalArgumentException( "The base URL of the AI provider must be an http(s) URL." );
      }
      final String url = uri.toString();
      return url.endsWith( "/" ) ? url.substring( 0, url.length() - 1 ) : url;
   }

   private static @Nullable String blankToNull( final @Nullable String value ) {
      return value == null || value.isBlank() ? null : value.trim();
   }

   /**
    * @return whether the provider runs on this machine, i.e. a local provider or a base URL on the loopback interface
    */
   public boolean isLocal() {
      return kind.isLocal() || baseUrl != null && isLoopback( baseUrl );
   }

   private static boolean isLoopback( final String url ) {
      final String host = URI.create( url ).getHost();
      return "localhost".equalsIgnoreCase( host ) || "127.0.0.1".equals( host ) || "[::1]".equals( host ) || "::1".equals( host );
   }

   /**
    * @param newModel the model
    * @return a copy of the settings with another model
    */
   public AiProviderSettings withModel( final String newModel ) {
      return new AiProviderSettings( kind, newModel, baseUrl, apiVersion, apiKey );
   }

   /**
    * Removes the API key from a text, e.g. an error message of a provider.
    */
   public String scrub( final @Nullable String text ) {
      if ( text == null ) {
         return "";
      }
      return apiKey == null || apiKey.length() < 4 ? text : text.replace( apiKey, "***" );
   }

   @Override
   public String toString() {
      return "AiProviderSettings[kind=" + kind.id() + ", model=" + model + ", baseUrl=" + baseUrl + ", apiVersion=" + apiVersion
            + ", apiKey=" + ( apiKey == null ? "null" : "***" ) + "]";
   }
}
