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
package org.eclipse.esmf.ame.ai.copilot;

import static org.eclipse.esmf.ame.ai.copilot.CopilotFutures.await;
import static org.eclipse.esmf.ame.ai.copilot.CopilotFutures.sleep;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.model.copilot.CopilotDeviceLogin;
import org.eclipse.esmf.ame.ai.model.copilot.CopilotStatus;
import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;
import org.eclipse.esmf.ame.ai.provider.AiChatResult;
import org.eclipse.esmf.ame.ai.provider.AiErrors;
import org.eclipse.esmf.ame.ai.provider.AiToolSpec;
import org.eclipse.esmf.ame.config.ApplicationSettings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.AllowCopilotExperimental;
import com.github.copilot.CopilotClient;
import com.github.copilot.CopilotSession;
import com.github.copilot.SystemMessageMode;
import com.github.copilot.ffi.PlatformDetector;
import com.github.copilot.generated.AssistantMessageDeltaEvent;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.generated.AssistantUsageEvent;
import com.github.copilot.generated.SessionErrorEvent;
import com.github.copilot.generated.rpc.AccountLoginParams;
import com.github.copilot.generated.rpc.AccountLogoutParams;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.GetAuthStatusResponse;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionRequestResult;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
import com.github.copilot.rpc.ToolDefinition;
import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CopilotGateway} implemented with the GitHub Copilot SDK. The native Copilot runtime is bundled with the
 * platform-specific application jar; the client is started on first use and stopped on shutdown. Copilot uses the
 * logged-in user (credentials of the Copilot CLI or {@code gh}) or a login done via this gateway.
 */
@Singleton
@AllowCopilotExperimental
public class SdkCopilotGateway implements CopilotGateway {
   private static final Logger LOG = LoggerFactory.getLogger( SdkCopilotGateway.class );
   private static final String GITHUB_HOST = "https://github.com";
   private static final String CLIENT_NAME = "esmf-aspect-model-editor";

   private final ObjectMapper objectMapper = new ObjectMapper();
   private final Map<String, DeviceFlow> deviceFlows = new ConcurrentHashMap<>();
   private final Object clientLock = new Object();
   private @Nullable CopilotClient client;
   private @Nullable Boolean available;

   private record DeviceFlow( long intervalSeconds, Instant expiresAt ) {
   }

   @Override
   public boolean isAvailable() {
      if ( available == null ) {
         available = detectRuntime();
      }
      return available;
   }

   private static boolean detectRuntime() {
      try {
         final String classifier = PlatformDetector.detectClassifier();
         return CopilotClient.class.getClassLoader().getResource( "native/" + classifier + "/platform.properties" ) != null;
      } catch ( final RuntimeException | LinkageError exception ) {
         LOG.debug( "Copilot runtime not available: {}", exception.getMessage() );
         return false;
      }
   }

   private CopilotClient client() {
      if ( !isAvailable() ) {
         throw new AiException( AiErrorCode.PROVIDER_ERROR, "GitHub Copilot is not available in this build of the Aspect Model Editor." );
      }
      synchronized ( clientLock ) {
         if ( client == null ) {
            // the SDK replaces the whole process environment, so keep HOME, PATH, proxies etc.
            final Map<String, String> environment = new HashMap<>( System.getenv() );
            environment.put( "COPILOT_TELEMETRY_DISABLED", "true" );
            final CopilotClient newClient = new CopilotClient( new CopilotClientOptions()
                  .setUseLoggedInUser( true )
                  .setEnvironment( environment ) );
            await( newClient.start() );
            client = newClient;
         }
         return client;
      }
   }

   /**
    * Stops the Copilot client.
    */
   @PreDestroy
   public void close() {
      synchronized ( clientLock ) {
         if ( client != null ) {
            try {
               client.stop().get( 10, TimeUnit.SECONDS );
            } catch ( final Exception exception ) {
               LOG.debug( "Stopping the Copilot client failed: {}", exception.getMessage() );
               client.forceStop();
            }
            client = null;
         }
      }
   }

   @Override
   public CopilotStatus status() {
      if ( !isAvailable() ) {
         return new CopilotStatus( false, false, null, null );
      }
      final GetAuthStatusResponse status = await( client().getAuthStatus() );
      return new CopilotStatus( true, status.isAuthenticated(), status.getLogin(), status.getHost() );
   }

   private void requireAuthenticated() {
      if ( !status().authenticated() ) {
         throw new AiException( AiErrorCode.NOT_AUTHENTICATED, "Please log in to GitHub Copilot." );
      }
   }

   @Override
   public List<AiModelInfo> listModels() {
      requireAuthenticated();
      return await( client().listModels() ).stream()
            .map( model -> AiModelInfo.of( model.getId(), model.getName() ) )
            .toList();
   }

   @Override
   public AiChatResult chat( final ChatRequest request ) {
      requireAuthenticated();
      final CopilotClient copilot = client();
      final List<ToolDefinition> tools = new ArrayList<>();
      for ( final AiToolSpec tool : request.tools() ) {
         tools.add( ToolDefinition.createSkipPermission( tool.name(), tool.description(), tool.parameters(),
               invocation -> CompletableFuture.supplyAsync(
                     () -> request.toolExecutor().execute( invocation.getToolCallId(), invocation.getToolName(),
                           toJson( invocation.getArguments() ) ),
                     command -> Thread.ofVirtual().start( command ) ) ) );
      }
      final SessionConfig config = new SessionConfig()
            .setClientName( CLIENT_NAME )
            .setStreaming( true )
            .setEnableSessionTelemetry( false )
            .setSystemMessage( new SystemMessageConfig().setMode( SystemMessageMode.REPLACE ).setContent( request.systemPrompt() ) )
            .setTools( tools )
            // only our tools: no file, shell, web or MCP tools of Copilot
            .setAvailableTools( request.tools().stream().map( AiToolSpec::name ).toList() )
            .setOnPermissionRequest( ( permission, invocation ) -> CompletableFuture.completedFuture(
                  PermissionRequestResult.reject( "Not allowed in the Aspect Model Editor." ) ) )
            .setWorkingDirectory( workingDirectory() );
      if ( request.model() != null ) {
         config.setModel( request.model() );
      }

      final CopilotSession session = await( copilot.createSession( config ) );
      final StringBuilder text = new StringBuilder();
      final AtomicLong inputTokens = new AtomicLong();
      final AtomicLong outputTokens = new AtomicLong();
      final AtomicReference<SessionErrorEvent> error = new AtomicReference<>();
      try {
         session.on( AssistantMessageDeltaEvent.class, event -> {
            final String delta = event.getData() == null ? null : event.getData().deltaContent();
            if ( delta != null && !delta.isEmpty() && !request.cancellation().isCancelled() ) {
               text.append( delta );
               request.onDelta().accept( delta );
            }
         } );
         session.on( AssistantUsageEvent.class, event -> {
            if ( event.getData() != null ) {
               inputTokens.addAndGet( event.getData().inputTokens() == null ? 0 : event.getData().inputTokens() );
               outputTokens.addAndGet( event.getData().outputTokens() == null ? 0 : event.getData().outputTokens() );
            }
         } );
         session.on( SessionErrorEvent.class, error::set );
         request.cancellation().onCancel( () -> session.abort() );
         final CompletableFuture<AssistantMessageEvent> answer = session.sendAndWait( new MessageOptions().setPrompt( request.prompt() ),
               request.timeoutMillis() );
         request.cancellation().onCancel( () -> answer.cancel( false ) );
         final AssistantMessageEvent last = await( answer );
         request.cancellation().throwIfCancelled();
         if ( error.get() != null && text.isEmpty() ) {
            throw toException( error.get() );
         }
         if ( text.isEmpty() && last != null && last.getData() != null && last.getData().content() != null ) {
            text.append( last.getData().content() );
            request.onDelta().accept( last.getData().content() );
         }
         return new AiChatResult( text.toString(), inputTokens.get() == 0 ? null : inputTokens.get(),
               outputTokens.get() == 0 ? null : outputTokens.get() );
      } finally {
         closeQuietly( copilot, session );
      }
   }

   private static String workingDirectory() {
      final Path aiDirectory = ApplicationSettings.getAiConversationsPath().getParent();
      return aiDirectory.toAbsolutePath().toString();
   }

   private static void closeQuietly( final CopilotClient copilot, final CopilotSession session ) {
      final String sessionId = session.getSessionId();
      try {
         session.close();
         copilot.deleteSession( sessionId ).get( 10, TimeUnit.SECONDS );
      } catch ( final Exception exception ) {
         LOG.debug( "Closing the Copilot session failed: {}", exception.getMessage() );
      }
   }

   private static AiException toException( final SessionErrorEvent event ) {
      final SessionErrorEvent.SessionErrorEventData data = event.getData();
      final String message = data == null || data.message() == null ? "GitHub Copilot failed." : data.message();
      if ( data != null && data.statusCode() != null ) {
         return new AiException( AiErrors.codeOfStatus( data.statusCode().intValue() ), "GitHub Copilot: " + message );
      }
      return new AiException( AiErrorCode.PROVIDER_ERROR, "GitHub Copilot: " + message );
   }

   private String toJson( final Map<String, Object> arguments ) {
      try {
         return objectMapper.writeValueAsString( arguments == null ? Map.of() : arguments );
      } catch ( final JsonProcessingException exception ) {
         return "{}";
      }
   }

   @Override
   public CopilotDeviceLogin beginDeviceLogin() {
      final JsonNode result = rawRpc().invoke( "auth.login.begin", Map.of( "kind", "githubDotCom", "method", "device-code" ) );
      final String flowId = result.path( "flowId" ).asText( null );
      final JsonNode step = result.path( "step" );
      if ( flowId == null || !"device-code".equals( step.path( "kind" ).asText() ) ) {
         throw new AiException( AiErrorCode.PROVIDER_ERROR, "The device login is not supported by this Copilot runtime." );
      }
      final long expiresIn = step.path( "expiresIn" ).asLong( 900 );
      deviceFlows.put( flowId, new DeviceFlow( Math.max( 1, step.path( "interval" ).asLong( 5 ) ),
            Instant.now().plusSeconds( expiresIn ) ) );
      return new CopilotDeviceLogin( flowId, step.path( "userCode" ).asText(), step.path( "verificationUri" ).asText(), expiresIn );
   }

   @Override
   public CopilotStatus completeDeviceLogin( final String flowId ) {
      final DeviceFlow flow = deviceFlows.get( flowId );
      if ( flow == null ) {
         throw new NoSuchElementException( "Unknown login flow: " + flowId );
      }
      try {
         while ( Instant.now().isBefore( flow.expiresAt() ) ) {
            final JsonNode step = rawRpc().invoke( "auth.login.advance", Map.of( "flowId", flowId ) );
            final JsonNode current = step.has( "step" ) ? step.path( "step" ) : step;
            switch ( current.path( "kind" ).asText() ) {
               case "completed" -> {
                  return status();
               }
               case "error", "cancelled" -> throw new AiException( AiErrorCode.NOT_AUTHENTICATED,
                     "GitHub login failed: " + current.path( "message" ).asText( "unknown error" ) );
               default -> sleep( flow.intervalSeconds() );
            }
         }
         throw new AiException( AiErrorCode.TIMEOUT, "The GitHub login code expired." );
      } finally {
         deviceFlows.remove( flowId );
      }
   }

   @Override
   public void cancelDeviceLogin( final String flowId ) {
      deviceFlows.remove( flowId );
      try {
         rawRpc().invoke( "auth.login.cancel", Map.of( "flowId", flowId ) );
      } catch ( final AiException exception ) {
         LOG.debug( "Cancelling the GitHub login failed: {}", exception.getMessage() );
      }
   }

   @Override
   public CopilotStatus loginWithToken( final String token ) {
      if ( token == null || token.isBlank() ) {
         throw new IllegalArgumentException( "The token must not be empty." );
      }
      try {
         await( client().getRpc().account.login( AccountLoginParams.builder().host( GITHUB_HOST ).token( token.trim() ).build() ) );
      } catch ( final AiException exception ) {
         throw new AiException( AiErrorCode.INVALID_KEY, "GitHub rejected the token." );
      }
      final CopilotStatus status = status();
      if ( !status.authenticated() ) {
         throw new AiException( AiErrorCode.INVALID_KEY, "GitHub rejected the token." );
      }
      return status;
   }

   @Override
   public CopilotStatus logout() {
      if ( !isAvailable() ) {
         return status();
      }
      await( client().getRpc().account.logout( AccountLogoutParams.builder().build() ) );
      return status();
   }

   private CopilotRawRpc rawRpc() {
      return CopilotRawRpc.of( client().getRpc() );
   }
}
