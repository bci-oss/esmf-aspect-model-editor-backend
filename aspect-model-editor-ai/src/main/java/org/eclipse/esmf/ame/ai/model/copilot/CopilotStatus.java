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
package org.eclipse.esmf.ame.ai.model.copilot;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

/**
 * The state of the GitHub Copilot login.
 *
 * @param available whether the Copilot runtime is bundled with this application
 * @param authenticated whether a GitHub user is logged in
 * @param login the GitHub login of the user
 * @param host the GitHub host
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record CopilotStatus( boolean available, boolean authenticated, @Nullable String login, @Nullable String host ) {
}
