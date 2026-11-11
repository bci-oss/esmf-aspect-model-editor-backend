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

import java.io.IOException;

import org.eclipse.esmf.ame.ai.attachment.AiAttachmentStore;
import org.eclipse.esmf.ame.ai.attachment.AttachmentTextExtractor;
import org.eclipse.esmf.ame.ai.model.attachment.AiAttachment;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/**
 * The files attached to a chat with the AI assistant: their text is extracted and kept in memory, not persisted.
 */
@Controller( "ai" )
@Produces( MediaType.APPLICATION_JSON )
@ExecuteOn( TaskExecutors.BLOCKING )
public class AiAttachmentController {
   private final AiAttachmentStore attachmentStore;

   public AiAttachmentController( final AiAttachmentStore attachmentStore ) {
      this.attachmentStore = attachmentStore;
   }

   /**
    * Attaches a file to the chat. Files larger than {@link AttachmentTextExtractor#MAX_FILE_SIZE} are rejected before they are
    * read.
    *
    * @param file the PDF, Word, Excel or text file
    * @return the attachment
    * @throws IOException if the upload cannot be read
    */
   @Post( uri = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA )
   public HttpResponse<AiAttachment> addAttachment( @Part( "file" ) final CompletedFileUpload file ) throws IOException {
      AttachmentTextExtractor.checkFileSize( file.getSize() );
      return HttpResponse.ok( attachmentStore.add( file.getFilename(), file.getBytes() ) );
   }

   /**
    * Removes an attached file. Unknown ids are ignored.
    *
    * @param id the attachment id
    * @return 204 in any case
    */
   @Delete( "/attachments/{id}" )
   public HttpResponse<Void> deleteAttachment( @PathVariable final String id ) {
      attachmentStore.delete( id );
      return HttpResponse.noContent();
   }
}
