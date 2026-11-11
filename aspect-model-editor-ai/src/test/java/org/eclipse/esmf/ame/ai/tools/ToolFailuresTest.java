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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;

import org.junit.jupiter.api.Test;

class ToolFailuresTest {
   @Test
   void describesTheSharedFailures() {
      assertFailure( ToolFailures.noModelOpen(), "No model is open in the editor.", "No model open" );
      assertFailure( ToolFailures.readOnly(), ToolFailures.READ_ONLY, "read_only" );
      assertFailure( ToolFailures.invalidJson( new IOException( "broken" ) ), "Error: the arguments are not valid JSON: broken",
            "Invalid arguments" );
      assertFailure( ToolFailures.unknownTool( "deleteEverything" ), "Error: unknown tool 'deleteEverything'.", "Unknown tool" );
      assertFailure( ToolFailures.invalidArgument( new IllegalArgumentException( "Invalid URN: x" ) ), "Error: Invalid URN: x",
            "Invalid URN: x" );
      assertFailure( ToolFailures.failed( new IllegalStateException( "boom" ) ), "Error: boom", "Failed" );
      assertFailure( ToolFailures.failed( new IllegalStateException() ), "Error: IllegalStateException", "Failed" );
   }

   @Test
   void toolsReportTheSharedFailures() {
      final AiTools tools = new AiTools( null, null, null, null, null );
      final AiToolSession session = new AiToolSession( AiChatContext.EMPTY, proposal -> {
      } );

      assertFailure( tools.execute( session, AiTools.GET_ELEMENT, "{not json" ), null, "Invalid arguments" );
      assertFailure( tools.execute( session, "deleteEverything", null ), "Error: unknown tool 'deleteEverything'.", "Unknown tool" );
      assertFailure( tools.execute( session, AiTools.GET_OPEN_MODEL, null ), "No model is open in the editor.", "No model open" );
   }

   private static void assertFailure( final AiToolResult result, final String output, final String summary ) {
      assertTrue( result.failed() );
      if ( output != null ) {
         assertEquals( output, result.output() );
      }
      assertEquals( summary, result.summary() );
   }
}
