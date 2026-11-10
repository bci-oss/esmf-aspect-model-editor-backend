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
package org.eclipse.esmf.ame.ai.attachment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.eclipse.esmf.ame.ai.model.attachment.AiAttachment;

import org.junit.jupiter.api.Test;

class AiAttachmentStoreTest {
   private final AiAttachmentStore store = new AiAttachmentStore();

   private AiAttachment add( final String name, final String text ) {
      return store.add( name, text.getBytes( StandardCharsets.UTF_8 ) );
   }

   @Test
   void keepsTheExtractedTextUntilDeleted() {
      final AiAttachment attachment = add( "notes.txt", "Hello world" );
      assertEquals( "notes.txt", attachment.name() );
      assertEquals( "text", attachment.kind() );
      assertEquals( 11, attachment.size() );
      assertEquals( 11, attachment.characters() );
      assertEquals( "Hello world", store.get( attachment.id() ).orElseThrow().text() );
      assertNotEquals( attachment.id(), add( "notes.txt", "Hello world" ).id() );

      store.delete( attachment.id() );
      assertFalse( store.get( attachment.id() ).isPresent() );
      store.delete( "unknown" );
   }

   @Test
   void dropsTheLeastRecentlyUsedAttachments() {
      final AiAttachment first = add( "first.txt", "1" );
      final AiAttachment second = add( "second.txt", "2" );
      store.get( first.id() );
      for ( int i = 0; i < AiAttachmentStore.MAX_ATTACHMENTS - 1; i++ ) {
         add( "file" + i + ".txt", "x" );
      }
      assertTrue( store.get( first.id() ).isPresent() );
      assertFalse( store.get( second.id() ).isPresent() );
   }

   @Test
   void usesOnlyTheFileNameOfAPath() {
      assertEquals( "spec.md", add( "C:\\Users\\me\\spec.md", "x" ).name() );
      assertEquals( "spec.md", add( "/home/me/spec.md", "x" ).name() );
      assertEquals( "file", AiAttachmentStore.baseName( null ) );
      assertEquals( "file", AiAttachmentStore.baseName( "dir/" ) );
   }
}
