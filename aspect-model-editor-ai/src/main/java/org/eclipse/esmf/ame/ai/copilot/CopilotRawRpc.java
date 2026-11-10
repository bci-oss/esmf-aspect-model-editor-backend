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

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.copilot.generated.rpc.RpcCaller;
import com.github.copilot.generated.rpc.ServerRpc;

/**
 * Calls JSON-RPC methods of the Copilot runtime that the typed SDK API does not offer yet (the device login
 * {@code auth.login.*}).
 * <p>
 * The SDK keeps its {@link RpcCaller} in the private field {@code caller} of {@link ServerRpc}; it is read by
 * reflection. This is isolated here: if a future SDK version changes it, the device login fails with
 * {@code provider_error} and the UI falls back to logging in with a token.
 */
final class CopilotRawRpc {
   private static final long TIMEOUT_SECONDS = 30;

   private final RpcCaller caller;

   private CopilotRawRpc( final RpcCaller caller ) {
      this.caller = caller;
   }

   static CopilotRawRpc of( final ServerRpc serverRpc ) {
      try {
         final Field field = ServerRpc.class.getDeclaredField( "caller" );
         field.setAccessible( true );
         return new CopilotRawRpc( (RpcCaller) field.get( serverRpc ) );
      } catch ( final ReflectiveOperationException | RuntimeException exception ) {
         throw new AiException( AiErrorCode.PROVIDER_ERROR, "The device login is not supported by this Copilot runtime.", exception );
      }
   }

   JsonNode invoke( final String method, final Map<String, Object> params ) {
      try {
         return caller.invoke( method, params, JsonNode.class ).get( TIMEOUT_SECONDS, TimeUnit.SECONDS );
      } catch ( final InterruptedException exception ) {
         Thread.currentThread().interrupt();
         throw new AiException( AiErrorCode.PROVIDER_ERROR, "The Copilot request was interrupted.", exception );
      } catch ( final Exception exception ) {
         final Throwable cause = exception.getCause() == null ? exception : exception.getCause();
         throw new AiException( AiErrorCode.PROVIDER_ERROR, "Copilot request " + method + " failed: " + cause.getMessage(), exception );
      }
   }
}
