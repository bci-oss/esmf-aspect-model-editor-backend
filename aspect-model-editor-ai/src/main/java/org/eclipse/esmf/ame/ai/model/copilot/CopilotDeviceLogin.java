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

import io.micronaut.serde.annotation.Serdeable;

/**
 * A started GitHub device login: the user enters the code on the verification page.
 *
 * @param flowId the id of the login flow
 * @param userCode the code to enter
 * @param verificationUri the page to enter the code on
 * @param expiresIn the seconds until the code expires
 */
@Serdeable
public record CopilotDeviceLogin( String flowId, String userCode, String verificationUri, long expiresIn ) {
}
