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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A GitHub source together with the state of its local copy and the result of the last check.
 *
 * @param source the configuration of the source
 * @param path the directory of the local copy
 * @param synced whether a local copy exists
 * @param syncedSha the commit of the local copy
 * @param syncedAt when the local copy was created (ISO-8601)
 * @param remoteSha the latest commit of the branch or tag found by the last check
 * @param checkedAt when the source was checked last (ISO-8601)
 * @param updateAvailable whether the branch or tag points to another commit than the local copy
 * @param lastError the error of the last check or synchronization
 */
@Serdeable
@Introspected
public record GithubSourceStatus(
      GithubSourceConfig source,
      String path,
      boolean synced,
      @Nullable String syncedSha,
      @Nullable String syncedAt,
      @Nullable String remoteSha,
      @Nullable String checkedAt,
      boolean updateAvailable,
      @Nullable String lastError
) {}
