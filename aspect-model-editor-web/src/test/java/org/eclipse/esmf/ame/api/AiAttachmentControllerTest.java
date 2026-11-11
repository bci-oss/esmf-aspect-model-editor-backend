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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.eclipse.esmf.ame.ai.AiErrorCode;
import org.eclipse.esmf.ame.ai.AiException;
import org.eclipse.esmf.ame.ai.attachment.AiAttachmentStore;
import org.eclipse.esmf.ame.ai.attachment.AttachmentTextExtractor;
import org.eclipse.esmf.ame.ai.model.attachment.AiAttachment;

import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.FormFieldMetadata;
import org.junit.jupiter.api.Test;

class AiAttachmentControllerTest {
   private final AiAttachmentStore store = new AiAttachmentStore();
   private final AiAttachmentController controller = new AiAttachmentController( store );

   @Test
   void rejectsTooLargeFiles() {
      final CompletedFileUpload upload = upload( "big.txt", new byte[(int) AttachmentTextExtractor.MAX_FILE_SIZE + 1] );

      final AiException exception = assertThrows( AiException.class, () -> controller.addAttachment( upload ) );

      assertEquals( AiErrorCode.ATTACHMENT_TOO_LARGE, exception.getCode() );
      assertEquals( AttachmentTextExtractor.TOO_LARGE_MESSAGE, exception.getMessage() );
   }

   @Test
   void attachesAndDeletesFiles() throws IOException {
      final CompletedFileUpload upload = upload( "notes.txt", "Fleet notes".getBytes( StandardCharsets.UTF_8 ) );

      final HttpResponse<AiAttachment> response = controller.addAttachment( upload );
      final AiAttachment attachment = response.body();

      assertEquals( "notes.txt", attachment.name() );
      assertEquals( AttachmentTextExtractor.TEXT, attachment.kind() );
      assertEquals( 204, controller.deleteAttachment( attachment.id() ).code() );
   }

   private static CompletedFileUpload upload( final String fileName, final byte[] content ) {
      return CompletedFileUpload.ofMemory( new FormFieldMetadata( "file", fileName, MediaType.APPLICATION_OCTET_STREAM_TYPE ),
            ReadBufferFactory.getJdkFactory().adapt( content ) );
   }
}
