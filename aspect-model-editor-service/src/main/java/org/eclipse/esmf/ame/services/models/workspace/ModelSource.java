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

package org.eclipse.esmf.ame.services.models.workspace;

import java.nio.file.Path;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A location models are read from: a local workspace or the local copy of a GitHub source.
 *
 * @param type {@link #WORKSPACE} or {@link #GITHUB}
 * @param id the workspace path or the GitHub source id
 * @param name a readable name
 * @param path the absolute root directory
 * @param readOnly whether models of this source must not be changed
 * @param defaultWorkspace whether this is the default workspace of the application
 */
@Serdeable
@Introspected
public record ModelSource(
      String type,
      String id,
      String name,
      String path,
      boolean readOnly,
      boolean defaultWorkspace
) {
   public static final String WORKSPACE = "workspace";
   public static final String GITHUB = "github";

   public Path root() {
      return Path.of( path );
   }

   public boolean contains( final Path file ) {
      return file.toAbsolutePath().normalize().startsWith( root() );
   }
}
