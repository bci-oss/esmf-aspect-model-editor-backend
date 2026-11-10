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
package org.eclipse.esmf.ame.ai.model.proposal;

import io.micronaut.serde.annotation.Serdeable;

/**
 * A refactoring the frontend executes with the existing refactoring API.
 *
 * @param kind {@code renameElement}
 * @param urn the element to rename
 * @param newName the new local name
 */
@Serdeable
public record AiRefactoringProposal( String kind, String urn, String newName ) {
   public static final String RENAME_ELEMENT = "renameElement";
}
