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

package org.eclipse.esmf.ame.services.models.refactoring;

/**
 * The refactorings that are applied to all model sources with the ESMF SDK.
 */
public enum RefactoringType {
   /** Renames an element and updates all references to it. */
   RENAME_ELEMENT,
   /** Moves an element into another (new or existing) file, optionally of another namespace. */
   MOVE_ELEMENT,
   /** Renames a namespace version, moves its files to the new location and updates all references. */
   RENAME_NAMESPACE,
   /** Copies a namespace version or a single file to an increased version. */
   NEW_VERSION,
   /** Renames an Aspect Model file within its namespace version. */
   RENAME_FILE,
   /** Moves a single Aspect Model file into another namespace version and updates all references to its elements. */
   MOVE_FILE
}
