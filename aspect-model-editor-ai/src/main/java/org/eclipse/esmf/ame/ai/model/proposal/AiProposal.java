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
package org.eclipse.esmf.ame.ai.model.proposal;

import org.eclipse.esmf.ame.ai.model.validation.AiValidationError;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

/**
 * A change proposed by the AI. Nothing is written until the user applies it in the editor.
 *
 * @param id the id of the proposal
 * @param kind {@code modelChange}, {@code newModel}, {@code refactoring}, {@code payload} or {@code review}
 * @param title a short title
 * @param summary a markdown summary
 * @param turtle modelChange/newModel: the complete new Turtle of the model file
 * @param originalTurtle modelChange: the Turtle the change is based on
 * @param namespace newModel: the suggested namespace
 * @param version newModel: the suggested version
 * @param fileName newModel: the suggested file name
 * @param valid modelChange/newModel: whether the Turtle is a valid model
 * @param validationErrors modelChange/newModel: the validation errors
 * @param refactoring refactoring: the refactoring to execute
 * @param payload payload: the sample payloads
 * @param findings review: the findings
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiProposal(
      String id,
      String kind,
      String title,
      @Nullable String summary,
      @Nullable String turtle,
      @Nullable String originalTurtle,
      @Nullable String namespace,
      @Nullable String version,
      @Nullable String fileName,
      @Nullable Boolean valid,
      @Nullable List<AiValidationError> validationErrors,
      @Nullable AiRefactoringProposal refactoring,
      @Nullable List<AiPayloadVariant> payload,
      @Nullable List<AiReviewFinding> findings
) {
   public static final String MODEL_CHANGE = "modelChange";
   public static final String NEW_MODEL = "newModel";
   public static final String REFACTORING = "refactoring";
   public static final String PAYLOAD = "payload";
   public static final String REVIEW = "review";
}
