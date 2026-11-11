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

/**
 * Extracts the text of one file format.
 */
interface FormatExtractor {
   /**
    * @param content the content of the file, at most {@link AttachmentTextExtractor#MAX_FILE_SIZE} bytes
    * @return the extracted text
    * @throws org.eclipse.esmf.ame.ai.AiException if the file is unreadable or too large when unpacked
    */
   AttachmentTextExtractor.Extracted extract( byte[] content );
}
