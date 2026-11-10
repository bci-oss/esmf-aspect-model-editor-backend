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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A model offered by an AI provider.
 *
 * @param id the id used in requests
 * @param name the display name
 */
@Serdeable
public record AiModelInfo( String id, String name ) {
   /**
    * Creates a model info that falls back to the id when the provider reports no display name.
    *
    * @param id the id used in requests
    * @param name the display name or {@code null}
    * @return the model info
    */
   public static AiModelInfo of( final String id, final @Nullable String name ) {
      return new AiModelInfo( id, name == null ? id : name );
   }
}
