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
package org.eclipse.esmf.ame.ai.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.ai.TestDirectories;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversation;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversationMessage;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversationSummary;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConversationStoreTest {
   private Path directory;
   private ConversationStore store;

   @BeforeEach
   void setUp() {
      directory = TestDirectories.create( "conversations" );
      store = new ConversationStore( directory );
   }

   @AfterEach
   void tearDown() {
      TestDirectories.delete( directory );
   }

   private static AiConversation conversation( final String title, final String updatedAt ) {
      final AiConversationMessage message = new AiConversationMessage( "m1", AiConversationMessage.USER, "Hello", null, null, updatedAt,
            null, null );
      return new AiConversation( ConversationStore.newId(), title, updatedAt, updatedAt, "openai", "gpt-4o", List.of( message ) );
   }

   @Test
   void savesReadsAndListsConversationsNewestFirst() {
      final AiConversation older = store.save( conversation( "Older", "2025-01-01T10:00:00Z" ) );
      final AiConversation newer = store.save( conversation( "Newer", "2025-01-02T10:00:00Z" ) );

      assertEquals( List.of( "Newer", "Older" ), store.list().stream().map( AiConversationSummary::title ).toList() );
      assertEquals( older, store.get( older.id() ) );
      assertEquals( 1, store.get( newer.id() ).messages().size() );
      assertTrue( Files.exists( directory.resolve( older.id() + ".json" ) ) );
   }

   @Test
   void writesAtomicallyWithoutLeftoverTemporaryFiles() throws Exception {
      final AiConversation conversation = conversation( "First", "2025-01-01T10:00:00Z" );
      store.save( conversation );
      store.save( conversation.withTitle( "Second", "2025-01-01T11:00:00Z" ) );

      try ( final Stream<Path> files = Files.list( directory ) ) {
         assertEquals( List.of( conversation.id() + ".json" ), files.map( file -> file.getFileName().toString() ).toList() );
      }
      assertEquals( "Second", store.get( conversation.id() ).title() );
   }

   @Test
   void renamesConversation() {
      final AiConversation conversation = store.save( conversation( "Old", "2025-01-01T10:00:00Z" ) );

      final AiConversationSummary summary = store.rename( conversation.id(), "  New title " );

      assertEquals( "New title", summary.title() );
      assertEquals( "New title", store.get( conversation.id() ).title() );
      assertThrows( IllegalArgumentException.class, () -> store.rename( conversation.id(), " " ) );
   }

   @Test
   void deletesConversations() {
      final AiConversation first = store.save( conversation( "First", "2025-01-01T10:00:00Z" ) );
      store.save( conversation( "Second", "2025-01-01T10:00:00Z" ) );

      store.delete( first.id() );
      assertTrue( store.find( first.id() ).isEmpty() );
      assertThrows( NoSuchElementException.class, () -> store.delete( first.id() ) );

      store.deleteAll();
      assertTrue( store.list().isEmpty() );
   }

   @Test
   void ignoresUnreadableAndForeignFiles() throws Exception {
      store.save( conversation( "Valid", "2025-01-01T10:00:00Z" ) );
      Files.writeString( directory.resolve( ConversationStore.newId() + ".json" ), "{ not json" );
      Files.writeString( directory.resolve( "notes.json" ), "{}" );

      assertEquals( List.of( "Valid" ), store.list().stream().map( AiConversationSummary::title ).toList() );
      store.deleteAll();
      assertTrue( Files.exists( directory.resolve( "notes.json" ) ) );
   }

   @ParameterizedTest
   @ValueSource( strings = { "../secret", "..\\secret", "../../etc/passwd", "/etc/passwd", "C:\\Windows\\win.ini", "abc",
         "00000000-0000-0000-0000-00000000000g", "00000000-0000-0000-0000-000000000000/../x", "" } )
   void rejectsIdsThatAreNoUuids( final String id ) throws Exception {
      final Path outside = directory.resolveSibling( "secret.json" );
      Files.writeString( outside, "{}" );
      try {
         assertFalse( ConversationStore.isValidId( id ) );
         assertTrue( store.find( id ).isEmpty() );
         assertThrows( NoSuchElementException.class, () -> store.get( id ) );
         assertThrows( NoSuchElementException.class, () -> store.delete( id ) );
         assertThrows( NoSuchElementException.class, () -> store.rename( id, "x" ) );
         final AiConversation conversation = conversation( "x", "2025-01-01T10:00:00Z" );
         assertThrows( IllegalArgumentException.class, () -> store.save( new AiConversation( id, "x", conversation.createdAt(),
               conversation.updatedAt(), null, null, List.of() ) ) );
         assertTrue( Files.exists( outside ) );
      } finally {
         Files.deleteIfExists( outside );
      }
   }

   @Test
   void acceptsCanonicalUuidsOnly() {
      assertTrue( ConversationStore.isValidId( ConversationStore.newId() ) );
      assertFalse( ConversationStore.isValidId( null ) );
   }
}
