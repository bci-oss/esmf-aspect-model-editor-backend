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

import java.nio.charset.StandardCharsets;

import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.FormFieldMetadata;

/**
 * Wraps Turtle text as an in-memory upload, so that the existing services working on uploads can be reused.
 */
public final class TurtleUploads {
   private TurtleUploads() {
   }

   /**
    * Wraps a Turtle text as a file upload, as expected by the services of the editor.
    *
    * @param turtle the Turtle text
    * @param fileName the file name
    * @return the upload
    */
   public static CompletedFileUpload of( final String turtle, final String fileName ) {
      final FormFieldMetadata metadata = new FormFieldMetadata( "aspectModel", fileName, MediaType.of( "text/turtle" ) );
      return CompletedFileUpload.ofMemory( metadata, ReadBufferFactory.getJdkFactory().adapt( turtle.getBytes( StandardCharsets.UTF_8 ) ) );
   }
}
