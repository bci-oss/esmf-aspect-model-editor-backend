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
 * Describes the local copy of a GitHub source; stored as {@code .ame-source.json} in its directory.
 *
 * @param sha the synchronized commit
 * @param syncedAt when the copy was created (ISO-8601)
 * @param source the display name of the source at synchronization time
 */
@Serdeable
@Introspected
public record GithubSourceMetadata( String sha, String syncedAt, String source ) {}
