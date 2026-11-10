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
package org.eclipse.esmf.ame.ai.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.esmf.ame.ai.attachment.AiAttachmentStore;
import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;
import org.eclipse.esmf.ame.ai.model.chat.AiContextModel;
import org.eclipse.esmf.ame.ai.model.conversation.AiMessageContextRef;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService.WorkspaceFile;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the files and models the user added to the chat context. Model selections are matched against the listed
 * workspace files, so that only files of the configured sources can be read.
 */
@Singleton
public class AiContextResolver {
   static final int MAX_MODEL_FILES = 200;
   private static final Logger LOG = LoggerFactory.getLogger( AiContextResolver.class );

   private final AiAttachmentStore attachmentStore;
   private final WorkspaceReferenceService workspaceReferenceService;

   public AiContextResolver( final AiAttachmentStore attachmentStore, final WorkspaceReferenceService workspaceReferenceService ) {
      this.attachmentStore = attachmentStore;
      this.workspaceReferenceService = workspaceReferenceService;
   }

   /**
    * An attached file.
    *
    * @param id the attachment id
    * @param name the file name
    * @param text the extracted text
    * @param truncated whether the text was cut at the limit
    */
   public record Attachment( String id, String name, String text, boolean truncated ) {
   }

   /**
    * A model file added as context.
    *
    * @param namespace the namespace
    * @param version the version
    * @param fileName the file name
    * @param readOnly whether the file comes from a read-only source
    * @param content the Turtle, {@code null} if it is not inlined in the prompt
    */
   public record ModelFile( String namespace, String version, String fileName, boolean readOnly, @Nullable String content ) {
      /**
       * @return {@code namespace:version/fileName}
       */
      public String label() {
         return namespace + ":" + version + "/" + fileName;
      }
   }

   /**
    * The resolved context.
    *
    * @param attachments the attachments still kept
    * @param missingAttachmentIds the attachments that are no longer available
    * @param modelFiles the selected model files
    * @param omittedModelFiles how many selected model files are not listed because there are too many
    * @param refs the references stored with the user message
    */
   public record Resolved( List<Attachment> attachments, List<String> missingAttachmentIds, List<ModelFile> modelFiles,
         int omittedModelFiles, List<AiMessageContextRef> refs ) {
      public static final Resolved EMPTY = new Resolved( List.of(), List.of(), List.of(), 0, List.of() );

      /**
       * @return whether nothing was added to the context
       */
      public boolean isEmpty() {
         return attachments.isEmpty() && missingAttachmentIds.isEmpty() && modelFiles.isEmpty();
      }
   }

   /**
    * Resolves the attachments and context models of a chat context.
    *
    * @param context the chat context
    * @return the resolved context
    */
   public Resolved resolve( final AiChatContext context ) {
      final List<String> attachmentIds = context.attachments();
      final List<AiContextModel> models = context.models();
      if ( attachmentIds.isEmpty() && models.isEmpty() ) {
         return Resolved.EMPTY;
      }
      final List<AiMessageContextRef> refs = new ArrayList<>();
      final List<Attachment> attachments = new ArrayList<>();
      final List<String> missing = new ArrayList<>();
      for ( final String id : attachmentIds ) {
         final Optional<AiAttachmentStore.Stored> stored = attachmentStore.get( id );
         if ( stored.isPresent() ) {
            final AiAttachmentStore.Stored attachment = stored.get();
            attachments.add( new Attachment( id, attachment.info().name(), attachment.text(), attachment.info().truncated() ) );
            refs.add( new AiMessageContextRef( AiMessageContextRef.FILE, attachment.info().name(), id ) );
         } else {
            missing.add( id );
         }
      }

      final Map<Path, WorkspaceFile> selected = new LinkedHashMap<>();
      if ( !models.isEmpty() ) {
         final List<WorkspaceFile> files = workspaceFiles();
         for ( final AiContextModel model : models ) {
            refs.add( ref( model ) );
            files.stream().filter( file -> matches( model, file ) ).forEach( file -> selected.putIfAbsent( file.path(), file ) );
         }
      }
      final List<ModelFile> modelFiles = new ArrayList<>();
      int budget = AiPrompts.MAX_MODEL_LENGTH;
      for ( final WorkspaceFile file : selected.values().stream().limit( MAX_MODEL_FILES ).toList() ) {
         final String content = read( file.path(), budget );
         if ( content != null ) {
            budget -= content.length();
         }
         modelFiles.add( new ModelFile( file.namespace(), file.version(), file.fileName(), readOnly( file.path() ), content ) );
      }
      return new Resolved( List.copyOf( attachments ), List.copyOf( missing ), List.copyOf( modelFiles ),
            Math.max( 0, selected.size() - MAX_MODEL_FILES ), List.copyOf( refs ) );
   }

   private List<WorkspaceFile> workspaceFiles() {
      try {
         return workspaceReferenceService.listWorkspaceFiles();
      } catch ( final RuntimeException exception ) {
         LOG.warn( "The workspace files could not be listed: {}", exception.getMessage() );
         return List.of();
      }
   }

   private boolean readOnly( final Path file ) {
      try {
         return workspaceReferenceService.isReadOnly( file );
      } catch ( final RuntimeException exception ) {
         return false;
      }
   }

   static boolean matches( final AiContextModel model, final WorkspaceFile file ) {
      if ( !isBlank( model.sourcePath() ) && !sameRoot( model.sourcePath(), file.root() ) ) {
         return false;
      }
      if ( isBlank( model.namespace() ) ) {
         return true;
      }
      return model.namespace().equals( file.namespace() )
            && ( isBlank( model.version() ) || model.version().equals( file.version() ) )
            && ( isBlank( model.fileName() ) || model.fileName().equals( file.fileName() ) );
   }

   private static boolean sameRoot( final String sourcePath, final Path root ) {
      try {
         return normalize( Path.of( sourcePath ) ).equals( normalize( root ) );
      } catch ( final InvalidPathException exception ) {
         return false;
      }
   }

   private static Path normalize( final Path path ) {
      return path.toAbsolutePath().normalize();
   }

   static AiMessageContextRef ref( final AiContextModel model ) {
      if ( !isBlank( model.namespace() ) ) {
         final String namespace = model.namespace() + ( isBlank( model.version() ) ? "" : ":" + model.version() );
         return isBlank( model.fileName() ) || isBlank( model.version() )
               ? new AiMessageContextRef( AiMessageContextRef.NAMESPACE, namespace, null )
               : new AiMessageContextRef( AiMessageContextRef.MODEL, namespace + "/" + model.fileName(), null );
      }
      return new AiMessageContextRef( AiMessageContextRef.SOURCE, sourceName( Objects.requireNonNull( model.sourcePath() ) ), null );
   }

   private static String sourceName( final String sourcePath ) {
      final String trimmed = sourcePath.replaceAll( "[/\\\\]+$", "" );
      final int separator = Math.max( trimmed.lastIndexOf( '/' ), trimmed.lastIndexOf( '\\' ) );
      final String name = separator < 0 ? trimmed : trimmed.substring( separator + 1 );
      return name.isEmpty() ? sourcePath : name;
   }

   private static @Nullable String read( final Path file, final int budget ) {
      try {
         if ( budget <= 0 || Files.size( file ) > budget ) {
            return null;
         }
         final String content = Files.readString( file, StandardCharsets.UTF_8 );
         return content.length() > budget ? null : content;
      } catch ( final IOException | RuntimeException exception ) {
         LOG.debug( "The context model {} could not be read", file, exception );
         return null;
      }
   }

   private static boolean isBlank( final @Nullable String value ) {
      return value == null || value.isBlank();
   }
}
