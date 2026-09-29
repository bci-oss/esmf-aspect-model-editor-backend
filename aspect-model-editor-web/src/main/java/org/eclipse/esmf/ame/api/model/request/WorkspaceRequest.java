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

package org.eclipse.esmf.ame.api.model.request;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Request payload for workspace management operations.
 *
 * @param path the directory path of the workspace
 * @param setActive whether to set this workspace as active (optional, defaults to false)
 */
@Serdeable
@Introspected
public record WorkspaceRequest(
      String path,
      @Nullable Boolean setActive
) {
   public WorkspaceRequest( final String path ) {
      this( path, null );
   }
}
