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
package org.eclipse.esmf.ame.ai.model.provider;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

/**
 * Result of a connection test.
 *
 * @param ok whether the provider answered
 * @param code the error code if not
 * @param message the error message if not
 * @param models the models of the provider if it answered
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiTestResult( boolean ok, @Nullable String code, @Nullable String message, @Nullable List<AiModelInfo> models ) {
   /**
    * @param models the models of the provider
    * @return a successful result
    */
   public static AiTestResult success( final List<AiModelInfo> models ) {
      return new AiTestResult( true, null, null, models );
   }

   /**
    * @param code the error code
    * @param message the message
    * @return a failed result
    */
   public static AiTestResult failure( final String code, final String message ) {
      return new AiTestResult( false, code, message, null );
   }
}
