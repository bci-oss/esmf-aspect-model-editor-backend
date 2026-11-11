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
package org.eclipse.esmf.ame.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.esmf.ame.ai.chat.AiChatService;
import org.eclipse.esmf.ame.ai.model.chat.AiChatEvent;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.sse.Event;
import org.junit.jupiter.api.Test;

class AiChatControllerTest {
   @Test
   void chatEventsBecomeNamedServerSentEvents() {
      final AiChatEvent delta = AiChatEvent.delta( "Hello" );

      final Event<Object> event = AiChatController.toServerSentEvent( delta );

      assertEquals( AiChatEvent.DELTA, event.getName() );
      assertSame( delta.data(), event.getData() );
   }

   @Test
   void cancelAnswersNoContentForRunningAndUnknownRequests() {
      final AiChatService chatService = mock( AiChatService.class );
      when( chatService.cancel( "running" ) ).thenReturn( true );
      final AiChatController controller = new AiChatController( chatService );

      assertEquals( HttpStatus.NO_CONTENT, controller.cancelChat( "running" ).getStatus() );
      assertEquals( HttpStatus.NO_CONTENT, controller.cancelChat( "unknown" ).getStatus() );
      verify( chatService ).cancel( "running" );
      verify( chatService ).cancel( "unknown" );
   }
}
