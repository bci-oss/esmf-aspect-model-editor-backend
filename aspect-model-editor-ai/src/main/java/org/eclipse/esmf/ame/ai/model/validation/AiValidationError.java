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
package org.eclipse.esmf.ame.ai.model.validation;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

/**
 * A validation error of a model.
 *
 * @param message the message
 * @param focusNode the URN of the element the error is about
 * @param resultPath the property the error is about
 * @param errorCode the code of the error
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiValidationError( String message, @Nullable String focusNode, @Nullable String resultPath, @Nullable String errorCode ) {
   /**
    * Creates an error without result path and error code.
    *
    * @param message the message
    * @param focusNode the URN of the element the error is about
    */
   public AiValidationError( final String message, @Nullable final String focusNode ) {
      this( message, focusNode, null, null );
   }
}
