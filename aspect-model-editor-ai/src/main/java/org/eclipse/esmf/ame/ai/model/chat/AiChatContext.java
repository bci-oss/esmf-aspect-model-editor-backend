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
package org.eclipse.esmf.ame.ai.model.chat;

import org.eclipse.esmf.ame.ai.model.validation.AiValidationError;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

/**
 * What the user sees in the editor when sending a chat message.
 *
 * @param modelTurtle the opened (possibly unsaved) model as Turtle
 * @param modelUrn the URN of the aspect (or first element) of the opened model
 * @param namespace the namespace of the opened model
 * @param version the version of the opened model
 * @param fileName the file name of the opened model
 * @param workspaceId the workspace of the opened model
 * @param readOnly whether the opened model comes from a read-only source
 * @param selectedElementUrn the selected element
 * @param mentionedElementUrns the elements mentioned by the user
 * @param validationErrors the current validation errors
 * @param languages the target languages for descriptions and translations
 * @param attachmentIds the attached files
 * @param contextModels the workspace or GitHub models added as context
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiChatContext(
      @Nullable String modelTurtle,
      @Nullable String modelUrn,
      @Nullable String namespace,
      @Nullable String version,
      @Nullable String fileName,
      @Nullable String workspaceId,
      @Nullable Boolean readOnly,
      @Nullable String selectedElementUrn,
      @Nullable List<String> mentionedElementUrns,
      @Nullable List<AiValidationError> validationErrors,
      @Nullable List<String> languages,
      @Nullable List<String> attachmentIds,
      @Nullable List<AiContextModel> contextModels
) {
   public static final AiChatContext EMPTY = new AiChatContext( null, null, null, null, null, null, null, null, null, null, null, null, null );

   /**
    * @return whether the model must not be changed
    */
   public boolean isReadOnly() {
      return Boolean.TRUE.equals( readOnly );
   }

   /**
    * @return whether the context contains the Turtle text of the open model
    */
   public boolean hasModel() {
      return modelTurtle != null && !modelTurtle.isBlank();
   }

   /**
    * @return the attached files without blanks and duplicates
    */
   public List<String> attachments() {
      return attachmentIds == null ? List.of() : attachmentIds.stream().filter( id -> id != null && !id.isBlank() ).distinct().toList();
   }

   /**
    * @return the valid context model selections
    */
   public List<AiContextModel> models() {
      return contextModels == null ? List.of() : contextModels.stream().filter( m -> m != null && m.isValid() ).distinct().toList();
   }
}
