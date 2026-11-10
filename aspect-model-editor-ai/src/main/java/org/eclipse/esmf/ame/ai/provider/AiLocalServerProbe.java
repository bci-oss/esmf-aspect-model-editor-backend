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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.esmf.ame.ai.model.provider.AiLocalServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Detects local OpenAI-compatible servers (Ollama, LM Studio) by requesting their model list on localhost.
 */
@Singleton
public class AiLocalServerProbe {
   static final Duration TIMEOUT = Duration.ofSeconds( 1 );

   private final ObjectMapper objectMapper = new ObjectMapper();
   private final Map<AiProviderKind, String> servers;

   @Inject
   public AiLocalServerProbe() {
      this( Map.of( AiProviderKind.OLLAMA, AiProviderKind.OLLAMA.defaultBaseUrl(), AiProviderKind.LMSTUDIO,
            AiProviderKind.LMSTUDIO.defaultBaseUrl() ) );
   }

   /**
    * @param servers the base URLs (ending with {@code /v1}) to probe per kind
    */
   public AiLocalServerProbe( final Map<AiProviderKind, String> servers ) {
      this.servers = new LinkedHashMap<>( servers );
   }

   /**
    * Probes all servers in parallel.
    *
    * @return the servers that answered, with their models
    */
   public List<AiLocalServer> probe() {
      final HttpClient client = AiHttpClients.jdkClientBuilder( TIMEOUT, false ).build();
      final List<CompletableFuture<Optional<AiLocalServer>>> futures = new ArrayList<>();
      servers.entrySet().stream()
            .sorted( Map.Entry.comparingByKey() )
            .forEach( server -> futures.add( probe( client, server.getKey(), server.getValue() ) ) );
      return futures.stream().map( CompletableFuture::join ).flatMap( Optional::stream ).toList();
   }

   private CompletableFuture<Optional<AiLocalServer>> probe( final HttpClient client, final AiProviderKind kind, final String baseUrl ) {
      final HttpRequest request = HttpRequest.newBuilder( URI.create( baseUrl + "/models" ) ).timeout( TIMEOUT ).GET().build();
      return client.sendAsync( request, HttpResponse.BodyHandlers.ofString() )
            .thenApply( response -> response.statusCode() == 200
                  ? Optional.of( new AiLocalServer( kind.id(), baseUrl, models( response.body() ) ) )
                  : Optional.<AiLocalServer> empty() )
            .exceptionally( error -> Optional.empty() );
   }

   private List<String> models( final String body ) {
      final List<String> models = new ArrayList<>();
      try {
         final JsonNode data = objectMapper.readTree( body ).path( "data" );
         data.forEach( model -> {
            if ( model.hasNonNull( "id" ) ) {
               models.add( model.get( "id" ).asText() );
            }
         } );
      } catch ( final Exception e ) {
         // a server that answers without a model list has no models
      }
      return models;
   }
}
