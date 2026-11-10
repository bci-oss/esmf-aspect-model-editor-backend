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
package org.eclipse.esmf.ame.ai.model.attachment;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A file attached to the chat; its text was extracted and is kept in memory.
 *
 * @param id the id used in the chat context
 * @param name the original file name
 * @param kind {@code pdf}, {@code docx}, {@code xlsx} or {@code text}
 * @param size the size of the uploaded file in bytes
 * @param characters the length of the extracted text
 * @param pages the pages of a PDF or the sheets of a workbook
 * @param truncated whether the text was cut at the limit
 * @param warning a hint such as {@code no_text} for a scanned PDF without text layer
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiAttachment(
      String id,
      String name,
      String kind,
      long size,
      int characters,
      @Nullable Integer pages,
      boolean truncated,
      @Nullable String warning
) {
   public static final String NO_TEXT = "no_text";
}
