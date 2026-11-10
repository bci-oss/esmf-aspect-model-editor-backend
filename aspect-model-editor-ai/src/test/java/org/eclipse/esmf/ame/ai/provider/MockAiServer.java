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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A local HTTP server answering AI provider requests with scripted responses.
 */
final class MockAiServer implements AutoCloseable {
   record Response( int status, String contentType, String body ) {
      static Response json( final int status, final String body ) {
         return new Response( status, "application/json", body );
      }

      static Response sse( final String body ) {
         return new Response( 200, "text/event-stream", body );
      }
   }

   record Request( String method, String path, Map<String, List<String>> headers, String body ) {
      String header( final String name ) {
         return headers.entrySet().stream().filter( entry -> entry.getKey().equalsIgnoreCase( name ) )
               .map( entry -> entry.getValue().getFirst() ).findFirst().orElse( null );
      }
   }

   private final HttpServer server;
   private final Map<String, Queue<Response>> responses = new ConcurrentHashMap<>();
   private final List<Request> requests = new CopyOnWriteArrayList<>();

   MockAiServer() throws IOException {
      server = HttpServer.create( new InetSocketAddress( InetAddress.getLoopbackAddress(), 0 ), 0 );
      server.createContext( "/", this::handle );
      server.start();
   }

   String baseUrl() {
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
   }

   MockAiServer enqueue( final String path, final Response response ) {
      responses.computeIfAbsent( path, key -> new ConcurrentLinkedQueue<>() ).add( response );
      return this;
   }

   List<Request> requests() {
      return requests;
   }

   private void handle( final HttpExchange exchange ) throws IOException {
      final String path = exchange.getRequestURI().getPath();
      final String body = new String( exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8 );
      requests.add( new Request( exchange.getRequestMethod(), path, Map.copyOf( exchange.getRequestHeaders() ), body ) );
      final Queue<Response> queue = responses.get( path );
      final Response response = queue == null || queue.isEmpty() ? Response.json( 404, "{\"error\":{\"message\":\"not found\"}}" )
            : queue.poll();
      final byte[] bytes = response.body().getBytes( StandardCharsets.UTF_8 );
      exchange.getResponseHeaders().add( "Content-Type", response.contentType() );
      exchange.sendResponseHeaders( response.status(), bytes.length );
      try ( final OutputStream out = exchange.getResponseBody() ) {
         out.write( bytes );
      }
   }

   @Override
   public void close() {
      server.stop( 0 );
   }
}
