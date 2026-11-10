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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A finding of a model review.
 *
 * @param id the id of the finding
 * @param severity {@code info}, {@code warning} or {@code error}
 * @param category {@code naming}, {@code unit}, {@code description}, {@code characteristic}, {@code reuse} or
 *       {@code other}
 * @param elementUrn the element the finding is about
 * @param message the finding
 * @param suggestion how to fix it
 * @param reuseUrn an existing element to reuse instead
 * @param fixable whether the AI can fix it
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiReviewFinding(
      String id,
      String severity,
      String category,
      @Nullable String elementUrn,
      String message,
      @Nullable String suggestion,
      @Nullable String reuseUrn,
      boolean fixable
) {
   /** The allowed severities; the first one is the default. */
   public static final List<String> SEVERITIES = List.of( "info", "warning", "error" );
   /** The allowed categories; the last one is the default. */
   public static final List<String> CATEGORIES = List.of( "naming", "unit", "description", "characteristic", "reuse", "other" );
}
