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
package org.eclipse.esmf.ame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks the workspace event stream end to end: files changed by another program are pushed, files written while the
 * backend handles a request are not.
 */
@MicronautTest
@Property( name = "ame.workspace-watch.interval", value = "50ms" )
@Property( name = WorkspaceEventsIntegrationTest.ENABLED, value = "true" )
class WorkspaceEventsIntegrationTest {
   static final String ENABLED = "workspace-events-integration-test";
   private static final Duration TIMEOUT = Duration.ofSeconds( 10 );
   private static final Path WORKSPACE = createWorkspace();

   @Inject
   EmbeddedServer server;

   private final HttpClient http = HttpClient.newHttpClient();
   private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
   private InputStream stream;

   @MockBean( WorkspaceReferenceService.class )
   WorkspaceReferenceService workspaceReferenceService() {
      return new WorkspaceReferenceService( WORKSPACE );
   }

   @BeforeEach
   void openStream() throws Exception {
      final HttpResponse<InputStream> response = http.send(
            HttpRequest.newBuilder( server.getURI().resolve( "/ame/api/workspaces/events" ) ).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream() );
      assertEquals( 200, response.statusCode() );
      assertTrue( response.headers().firstValue( "content-type" ).orElseThrow().startsWith( "text/event-stream" ) );
      stream = response.body();
      Thread.ofVirtual().start( this::readEvents );
      assertEquals( "ready", nextEventName() );
   }

   @AfterEach
   void closeStream() throws IOException {
      stream.close();
   }

   @AfterAll
   static void deleteWorkspace() throws IOException {
      try ( final Stream<Path> paths = Files.walk( WORKSPACE ) ) {
         paths.sorted( Comparator.reverseOrder() ).forEach( path -> path.toFile().delete() );
      }
   }

   @Test
   void externalChangesArePushedButOwnWritesAreNot() throws Exception {
      final Path external = WORKSPACE.resolve( "org.example.vscode" ).resolve( "1.0.0" ).resolve( "External.ttl" );
      Files.createDirectories( external.getParent() );
      Files.writeString( external, "# written by VS Code" );

      final String externalEvent = nextChangeEvent();
      assertTrue( externalEvent.contains( "\"fileName\":\"External.ttl\"" ), externalEvent );
      assertTrue( externalEvent.contains( "\"namespace\":\"org.example.vscode\"" ), externalEvent );

      final HttpResponse<String> ownWrite = http.send(
            HttpRequest.newBuilder( server.getURI().resolve( "/ame/api/test/own-write" ) ).POST( HttpRequest.BodyPublishers.noBody() )
                  .build(), HttpResponse.BodyHandlers.ofString() );
      assertEquals( 200, ownWrite.statusCode() );
      Files.writeString( external, "# changed again by VS Code" );

      final String nextEvent = nextChangeEvent();
      assertTrue( nextEvent.contains( "\"modified\":[{" ) && nextEvent.contains( "External.ttl" ), nextEvent );
      assertTrue( !nextEvent.contains( "OwnWrite.ttl" ), "own write must not be reported: " + nextEvent );
   }

   private void readEvents() {
      try ( final BufferedReader reader = new BufferedReader( new InputStreamReader( stream, StandardCharsets.UTF_8 ) ) ) {
         String name = null;
         for ( String line = reader.readLine(); line != null; line = reader.readLine() ) {
            if ( line.startsWith( "event:" ) ) {
               name = line.substring( "event:".length() ).trim();
            } else if ( line.startsWith( "data:" ) && !"heartbeat".equals( name ) ) {
               events.add( name + " " + line.substring( "data:".length() ).trim() );
            }
         }
      } catch ( final IOException e ) {
         // The stream is closed after each test.
      }
   }

   private String nextEventName() throws InterruptedException {
      return nextEvent().split( " ", 2 )[0];
   }

   private String nextChangeEvent() throws InterruptedException {
      final String event = nextEvent();
      assertTrue( event.startsWith( "files-changed " ), event );
      return event;
   }

   private String nextEvent() throws InterruptedException {
      final String event = events.poll( TIMEOUT.toMillis(), TimeUnit.MILLISECONDS );
      assertTrue( event != null, "no event within " + TIMEOUT );
      return event;
   }

   private static Path createWorkspace() {
      try {
         return Files.createTempDirectory( "ame-workspace-events" ).toRealPath();
      } catch ( final IOException e ) {
         throw new UncheckedIOException( e );
      }
   }

   /**
    * Simulates an endpoint of the editor that saves a model file.
    */
   @Controller( "test/own-write" )
   @Requires( property = ENABLED, value = "true" )
   static class OwnWriteController {
      @Post
      String write() throws IOException {
         final Path file = WORKSPACE.resolve( "org.example.vscode" ).resolve( "1.0.0" ).resolve( "OwnWrite.ttl" );
         Files.createDirectories( file.getParent() );
         Files.writeString( file, "# saved by the editor" );
         return "saved";
      }
   }
}
