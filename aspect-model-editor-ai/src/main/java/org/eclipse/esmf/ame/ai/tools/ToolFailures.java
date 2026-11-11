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

import java.io.IOException;

/**
 * The failed tool results shared by several tools. The texts are returned to the AI, the summaries are shown to the user.
 */
final class ToolFailures {
   static final String NO_MODEL_OPEN = "No model is open in the editor.";
   static final String READ_ONLY = "read_only: The open model comes from a read-only source and must not be changed. "
         + "Explain the change instead, or propose a copy with proposeNewModel.";

   private ToolFailures() {
   }

   /**
    * @return the failure of a tool that needs an open model
    */
   static AiToolResult noModelOpen() {
      return AiToolResult.failure( NO_MODEL_OPEN, "No model open" );
   }

   /**
    * @return the failure of a tool that would change a read-only model
    */
   static AiToolResult readOnly() {
      return AiToolResult.failure( READ_ONLY, "read_only" );
   }

   /**
    * @return the failure for arguments that are no valid JSON
    */
   static AiToolResult invalidJson( final IOException exception ) {
      return AiToolResult.failure( "Error: the arguments are not valid JSON: " + exception.getMessage(), "Invalid arguments" );
   }

   /**
    * @return the failure for a tool that does not exist
    */
   static AiToolResult unknownTool( final String name ) {
      return AiToolResult.failure( "Error: unknown tool '" + name + "'.", "Unknown tool" );
   }

   /**
    * @return the failure for invalid arguments; the message is shown to the AI and the user
    */
   static AiToolResult invalidArgument( final IllegalArgumentException exception ) {
      return AiToolResult.failure( "Error: " + exception.getMessage(), exception.getMessage() );
   }

   /**
    * @return the failure for an unexpected error of a tool
    */
   static AiToolResult failed( final RuntimeException exception ) {
      final String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
      return AiToolResult.failure( "Error: " + message, "Failed" );
   }
}
