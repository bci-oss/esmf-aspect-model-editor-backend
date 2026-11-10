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

import org.eclipse.esmf.ame.ai.model.attachment.AiAttachment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Keeps the extracted text of attached files in memory. Attachments are not persisted; the oldest are dropped when the
 * store is full.
 */
@Singleton
public class AiAttachmentStore {
   public static final int MAX_ATTACHMENTS = 100;

   /**
    * An attachment with its text.
    *
    * @param info the attachment as sent to the client
    * @param text the extracted text
    */
   public record Stored( AiAttachment info, String text ) {
   }

   private final Map<String, Stored> attachments = new LinkedHashMap<>( 16, 0.75f, true ) {
      @Override
      protected boolean removeEldestEntry( final Map.Entry<String, Stored> eldest ) {
         return size() > MAX_ATTACHMENTS;
      }
   };

   /**
    * Extracts the text of a file and keeps it.
    *
    * @param fileName the name of the file
    * @param content the content of the file
    * @return the attachment
    * @throws org.eclipse.esmf.ame.ai.AiException if the text cannot be extracted
    */
   public AiAttachment add( final String fileName, final byte[] content ) {
      final String name = baseName( fileName );
      final AttachmentTextExtractor.Extracted extracted = AttachmentTextExtractor.extract( name, content );
      final AiAttachment info = new AiAttachment( UUID.randomUUID().toString(), name, extracted.kind(), content.length,
            extracted.text().length(), extracted.pages(), extracted.truncated(), extracted.warning() );
      synchronized ( attachments ) {
         attachments.put( info.id(), new Stored( info, extracted.text() ) );
      }
      return info;
   }

   /**
    * @param id the id of the attachment
    * @return the attachment if it is still kept
    */
   public Optional<Stored> get( final String id ) {
      synchronized ( attachments ) {
         return Optional.ofNullable( attachments.get( id ) );
      }
   }

   /**
    * Drops an attachment.
    *
    * @param id the id of the attachment
    */
   public void delete( final String id ) {
      synchronized ( attachments ) {
         attachments.remove( id );
      }
   }

   static String baseName( final @Nullable String fileName ) {
      if ( fileName == null || fileName.isBlank() ) {
         return "file";
      }
      final String name = fileName.substring( Math.max( fileName.lastIndexOf( '/' ), fileName.lastIndexOf( '\\' ) ) + 1 ).strip();
      return name.isEmpty() ? "file" : name;
   }
}
