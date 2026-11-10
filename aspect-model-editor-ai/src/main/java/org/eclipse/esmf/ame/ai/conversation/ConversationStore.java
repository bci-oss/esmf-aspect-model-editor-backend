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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.ai.model.conversation.AiConversation;
import org.eclipse.esmf.ame.ai.model.conversation.AiConversationSummary;
import org.eclipse.esmf.ame.config.ApplicationSettings;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists the AI conversations as one JSON file per conversation ({@code <id>.json}). Ids must be canonical UUIDs, so
 * a request can never address a file outside the conversation directory. Files are written atomically.
 */
@Singleton
public class ConversationStore {
   private static final Logger LOG = LoggerFactory.getLogger( ConversationStore.class );
   private static final String SUFFIX = ".json";

   private final ObjectMapper objectMapper = new ObjectMapper()
         .configure( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false )
         .enable( SerializationFeature.INDENT_OUTPUT );
   private final @Nullable Path configuredDirectory;
   private final Object lock = new Object();

   @Inject
   public ConversationStore( @Nullable @Property( name = "ame.ai.conversations-dir" ) final String directory ) {
      this( directory == null || directory.isBlank() ? null : Path.of( directory ) );
   }

   /**
    * @param directory the directory of the conversations or {@code null} for the default in the AME data directory
    */
   public ConversationStore( final @Nullable Path directory ) {
      this.configuredDirectory = directory;
   }

   /**
    * @return whether the text is a canonical UUID, i.e. a valid conversation id
    */
   public static boolean isValidId( final @Nullable String id ) {
      if ( id == null || id.length() != 36 ) {
         return false;
      }
      try {
         return UUID.fromString( id ).toString().equals( id.toLowerCase() );
      } catch ( final IllegalArgumentException exception ) {
         return false;
      }
   }

   /**
    * @return a new conversation id
    */
   public static String newId() {
      return UUID.randomUUID().toString();
   }

   /**
    * @return the summaries of all conversations, newest first
    */
   public List<AiConversationSummary> list() {
      final List<AiConversationSummary> summaries = new ArrayList<>();
      try ( final Stream<Path> files = Files.list( directory() ) ) {
         files.filter( this::isConversationFile ).forEach( file -> read( file ).ifPresent( c -> summaries.add( c.summary() ) ) );
      } catch ( final IOException exception ) {
         throw new UncheckedIOException( "Unable to list the AI conversations.", exception );
      }
      summaries.sort( Comparator.comparing( AiConversationSummary::updatedAt, Comparator.nullsLast( Comparator.naturalOrder() ) )
            .reversed() );
      return summaries;
   }

   /**
    * @param id the conversation id
    * @return the conversation, or empty if the id is invalid or unknown
    */
   public Optional<AiConversation> find( final @Nullable String id ) {
      if ( !isValidId( id ) ) {
         return Optional.empty();
      }
      final Path file = file( id );
      return Files.isRegularFile( file ) ? read( file ) : Optional.empty();
   }

   /**
    * @throws NoSuchElementException if there is no conversation with the id
    */
   public AiConversation get( final @Nullable String id ) {
      return find( id ).orElseThrow( () -> new NoSuchElementException( "AI conversation not found: " + id ) );
   }

   /**
    * Writes a conversation atomically.
    *
    * @param conversation the conversation
    * @return the conversation
    * @throws IllegalArgumentException if the id is invalid
    */
   public AiConversation save( final AiConversation conversation ) {
      if ( !isValidId( conversation.id() ) ) {
         throw new IllegalArgumentException( "Invalid AI conversation id." );
      }
      synchronized ( lock ) {
         final Path target = file( conversation.id() );
         final Path temp = target.resolveSibling( "." + conversation.id() + "." + UUID.randomUUID() + ".tmp" );
         try {
            objectMapper.writeValue( temp.toFile(), conversation );
            try {
               Files.move( temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING );
            } catch ( final AtomicMoveNotSupportedException exception ) {
               Files.move( temp, target, StandardCopyOption.REPLACE_EXISTING );
            }
         } catch ( final IOException exception ) {
            deleteQuietly( temp );
            throw new UncheckedIOException( "Unable to save the AI conversation.", exception );
         }
      }
      return conversation;
   }

   /**
    * @throws NoSuchElementException if there is no conversation with the id
    */
   public AiConversationSummary rename( final String id, final String title ) {
      if ( title == null || title.isBlank() ) {
         throw new IllegalArgumentException( "The title must not be empty." );
      }
      synchronized ( lock ) {
         final AiConversation conversation = get( id );
         return save( conversation.withTitle( title.strip(), Instant.now().toString() ) ).summary();
      }
   }

   /**
    * @throws NoSuchElementException if there is no conversation with the id
    */
   public void delete( final String id ) {
      if ( !isValidId( id ) ) {
         throw new NoSuchElementException( "AI conversation not found: " + id );
      }
      synchronized ( lock ) {
         try {
            if ( !Files.deleteIfExists( file( id ) ) ) {
               throw new NoSuchElementException( "AI conversation not found: " + id );
            }
         } catch ( final IOException exception ) {
            throw new UncheckedIOException( "Unable to delete the AI conversation.", exception );
         }
      }
   }

   /**
    * Deletes all conversations.
    */
   public void deleteAll() {
      synchronized ( lock ) {
         try ( final Stream<Path> files = Files.list( directory() ) ) {
            files.filter( this::isConversationFile ).forEach( ConversationStore::deleteQuietly );
         } catch ( final IOException exception ) {
            throw new UncheckedIOException( "Unable to delete the AI conversations.", exception );
         }
      }
   }

   private Path file( final String id ) {
      return directory().resolve( id.toLowerCase() + SUFFIX );
   }

   private boolean isConversationFile( final Path file ) {
      final String name = file.getFileName().toString();
      return Files.isRegularFile( file ) && name.endsWith( SUFFIX ) && isValidId( name.substring( 0, name.length() - SUFFIX.length() ) );
   }

   private Optional<AiConversation> read( final Path file ) {
      try {
         return Optional.of( objectMapper.readValue( file.toFile(), AiConversation.class ) );
      } catch ( final IOException exception ) {
         LOG.warn( "Ignoring unreadable AI conversation {}: {}", file.getFileName(), exception.getMessage() );
         return Optional.empty();
      }
   }

   private Path directory() {
      if ( configuredDirectory == null ) {
         return ApplicationSettings.getAiConversationsPath();
      }
      try {
         Files.createDirectories( configuredDirectory );
         return configuredDirectory;
      } catch ( final IOException exception ) {
         throw new UncheckedIOException( "Unable to create the AI conversation directory.", exception );
      }
   }

   private static void deleteQuietly( final Path file ) {
      try {
         Files.deleteIfExists( file );
      } catch ( final IOException exception ) {
         LOG.warn( "Unable to delete {}: {}", file.getFileName(), exception.getMessage() );
      }
   }
}
