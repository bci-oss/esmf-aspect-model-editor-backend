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

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A GitHub repository that provides Aspect Models read-only. The fields follow the GitHub model source configuration
 * of the ESMF SDK: the models are expected in the structured layout {@code <namespace>/<version>/<file>.ttl} below
 * {@code directory}. Access tokens are never part of the configuration.
 *
 * @param id the stable identifier, also the name of the local cache directory
 * @param host the GitHub host, e.g. {@code github.com}
 * @param owner the repository owner
 * @param repository the repository name
 * @param refType {@code branch} or {@code tag}
 * @param ref the branch or tag name
 * @param directory the directory inside the repository containing the models; empty for the repository root
 * @param enabled whether the source is used to resolve models
 */
@Serdeable
@Introspected
public record GithubSourceConfig(
      String id,
      String host,
      String owner,
      String repository,
      String refType,
      String ref,
      String directory,
      Boolean enabled
) {
   public static final String DEFAULT_HOST = "github.com";
   public static final String BRANCH = "branch";
   public static final String TAG = "tag";

   public GithubSourceConfig {
      host = host == null || host.isBlank() ? DEFAULT_HOST : host;
      refType = refType == null || refType.isBlank() ? BRANCH : refType;
      directory = directory == null ? "" : directory;
      enabled = enabled == null || enabled;
   }

   /**
    * A readable name, e.g. {@code eclipse-esmf/esmf-sdk@main/models}.
    */
   public String displayName() {
      final String prefix = DEFAULT_HOST.equals( host ) ? "" : host + "/";
      return prefix + owner + "/" + repository + "@" + ref + ( directory.isEmpty() ? "" : "/" + directory );
   }
}
