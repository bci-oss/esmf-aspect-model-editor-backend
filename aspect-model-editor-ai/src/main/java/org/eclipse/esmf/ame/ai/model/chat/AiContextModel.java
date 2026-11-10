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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Workspace or GitHub models the user added to the chat context: a file, a namespace (version) or a whole source.
 *
 * @param sourcePath the root directory of the source; {@code null} for all sources
 * @param namespace the namespace; {@code null} for the whole source
 * @param version the version; {@code null} for all versions of the namespace
 * @param fileName the file name; {@code null} for all files of the namespace version
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiContextModel(
      @Nullable String sourcePath,
      @Nullable String namespace,
      @Nullable String version,
      @Nullable String fileName
) {
   /**
    * @return whether the selection names a source or a namespace
    */
   public boolean isValid() {
      return !isBlank( sourcePath ) || !isBlank( namespace );
   }

   private static boolean isBlank( final @Nullable String value ) {
      return value == null || value.isBlank();
   }
}
