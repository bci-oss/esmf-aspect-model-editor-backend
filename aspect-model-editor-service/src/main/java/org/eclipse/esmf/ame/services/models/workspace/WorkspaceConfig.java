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

import java.util.List;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Configuration of the model sources of the Aspect Model Editor, stored in {@code workspaces.json}.
 *
 * @param version the format version of the configuration file
 * @param workspaces the local workspace directories; the default workspace is always the first entry
 * @param githubSources the read-only GitHub repositories whose models are used to resolve references
 */
@Serdeable
@Introspected
public record WorkspaceConfig(
      int version,
      List<String> workspaces,
      List<GithubSourceConfig> githubSources
) {
   public static final int CURRENT_VERSION = 2;

   @Creator
   public WorkspaceConfig {
      version = CURRENT_VERSION;
      workspaces = workspaces != null ? List.copyOf( workspaces ) : List.of();
      githubSources = githubSources != null ? List.copyOf( githubSources ) : List.of();
   }

   public WorkspaceConfig( final List<String> workspaces ) {
      this( CURRENT_VERSION, workspaces, List.of() );
   }

   public WorkspaceConfig withWorkspaces( final List<String> newWorkspaces ) {
      return new WorkspaceConfig( version, newWorkspaces, githubSources );
   }

   public WorkspaceConfig withGithubSources( final List<GithubSourceConfig> newGithubSources ) {
      return new WorkspaceConfig( version, workspaces, newGithubSources );
   }
}
