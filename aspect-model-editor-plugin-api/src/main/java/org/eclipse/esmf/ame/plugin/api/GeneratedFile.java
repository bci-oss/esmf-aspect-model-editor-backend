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

package org.eclipse.esmf.ame.plugin.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * A file created by a generator.
 *
 * @param fileName the name the file is suggested to be saved as, e.g. {@code Movement.json}; without directories
 * @param mediaType the media type of the content, e.g. {@code application/json}
 * @param content the content of the file
 */
public record GeneratedFile( String fileName, String mediaType, byte[] content ) {
   public GeneratedFile {
      Objects.requireNonNull( fileName, "fileName" );
      Objects.requireNonNull( mediaType, "mediaType" );
      content = Objects.requireNonNull( content, "content" ).clone();
   }

   @Override
   public byte[] content() {
      return content.clone();
   }

   @Override
   public boolean equals( final Object other ) {
      return other instanceof final GeneratedFile file && fileName.equals( file.fileName ) && mediaType.equals( file.mediaType )
            && Arrays.equals( content, file.content );
   }

   @Override
   public int hashCode() {
      return Objects.hash( fileName, mediaType, Arrays.hashCode( content ) );
   }

   @Override
   public String toString() {
      return "GeneratedFile[fileName=" + fileName + ", mediaType=" + mediaType + ", " + content.length + " bytes]";
   }
}
