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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.generated.rpc.RpcCaller;
import com.github.copilot.generated.rpc.ServerRpc;
import org.junit.jupiter.api.Test;

class CopilotRawRpcTest {
   private static final Map<String, Object> PARAMS = Map.of( "host", "https://github.com" );

   /**
    * Answers each call with the future of the script and records the calls.
    */
   private static final class ScriptedCaller implements RpcCaller {
      record Call( String method, Object params, Class<?> type ) {
      }

      private final Function<String, CompletableFuture<?>> script;
      private final List<Call> calls = new CopyOnWriteArrayList<>();

      ScriptedCaller( final Function<String, CompletableFuture<?>> script ) {
         this.script = script;
      }

      @Override
      @SuppressWarnings( "unchecked" )
      public <T> CompletableFuture<T> invoke( final String method, final Object params, final Class<T> type ) {
         calls.add( new Call( method, params, type ) );
         return (CompletableFuture<T>) script.apply( method );
      }
   }

   private static CopilotRawRpc rpc( final ScriptedCaller caller ) {
      return CopilotRawRpc.of( new ServerRpc( caller ) );
   }

   @Test
   void invokesTheMethodWithTheCallerOfTheSdk() throws Exception {
      final JsonNode result = new ObjectMapper().readTree( "{\"userCode\":\"ABCD-1234\"}" );
      final ScriptedCaller caller = new ScriptedCaller( method -> CompletableFuture.completedFuture( result ) );

      assertEquals( result, rpc( caller ).invoke( "auth.login.start", PARAMS ) );
      assertEquals( List.of( new ScriptedCaller.Call( "auth.login.start", PARAMS, JsonNode.class ) ), caller.calls );
   }

   @Test
   void reportsAFailedRequestWithItsCause() {
      final ScriptedCaller caller = new ScriptedCaller( method -> CompletableFuture.failedFuture( new IllegalStateException( "denied" ) ) );

      final AiException exception = assertThrows( AiException.class, () -> rpc( caller ).invoke( "auth.login.poll", PARAMS ) );

      assertEquals( AiErrorCode.PROVIDER_ERROR, exception.getCode() );
      assertEquals( "Copilot request auth.login.poll failed: denied", exception.getMessage() );
   }

   @Test
   void reportsACallerThatThrows() {
      final ScriptedCaller caller = new ScriptedCaller( method -> {
         throw new IllegalStateException( "connection closed" );
      } );

      final AiException exception = assertThrows( AiException.class, () -> rpc( caller ).invoke( "auth.login.start", PARAMS ) );

      assertEquals( AiErrorCode.PROVIDER_ERROR, exception.getCode() );
      assertEquals( "Copilot request auth.login.start failed: connection closed", exception.getMessage() );
   }

   @Test
   void keepsTheInterruptFlagWhenInterrupted() {
      final ScriptedCaller caller = new ScriptedCaller( method -> new CompletableFuture<>() );
      final CopilotRawRpc rpc = rpc( caller );

      Thread.currentThread().interrupt();
      try {
         final AiException exception = assertThrows( AiException.class, () -> rpc.invoke( "auth.login.poll", PARAMS ) );

         assertEquals( AiErrorCode.PROVIDER_ERROR, exception.getCode() );
         assertInstanceOf( InterruptedException.class, exception.getCause() );
         assertTrue( Thread.currentThread().isInterrupted() );
      } finally {
         Thread.interrupted();
      }
   }

   @Test
   void reportsAMissingServerRpcAsUnsupported() {
      final AiException exception = assertThrows( AiException.class, () -> CopilotRawRpc.of( null ) );

      assertEquals( AiErrorCode.PROVIDER_ERROR, exception.getCode() );
      assertEquals( "The device login is not supported by this Copilot runtime.", exception.getMessage() );
   }
}
