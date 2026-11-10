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
package org.eclipse.esmf.ame.ai.tools;

import io.micronaut.core.annotation.Nullable;

/**
 * The result of a tool call.
 *
 * @param output the text returned to the AI
 * @param summary a short summary shown to the user
 * @param failed whether the call failed
 */
public record AiToolResult( String output, @Nullable String summary, boolean failed ) {
   /**
    * @return a successful result
    */
   public static AiToolResult ok( final String output, final @Nullable String summary ) {
      return new AiToolResult( output, summary, false );
   }

   /**
    * @return a failed result
    */
   public static AiToolResult failure( final String output, final @Nullable String summary ) {
      return new AiToolResult( output, summary, true );
   }
}
