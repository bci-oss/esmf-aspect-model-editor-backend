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

import java.nio.charset.StandardCharsets;

/**
 * Reads text files as UTF-8 and drops a leading byte order mark.
 */
final class PlainTextExtractor implements FormatExtractor {
   @Override
   public AttachmentTextExtractor.Extracted extract( final byte[] content ) {
      String text = new String( content, StandardCharsets.UTF_8 );
      if ( text.startsWith( "\uFEFF" ) ) {
         text = text.substring( 1 );
      }
      return AttachmentTextExtractor.result( AttachmentTextExtractor.TEXT, text, null, null );
   }
}
