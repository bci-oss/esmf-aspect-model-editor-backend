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
 * The phases of a running refactoring, in the order in which they are passed.
 */
public enum RefactoringPhase {
   /** The files of all model sources are loaded. */
   LOADING,
   /** The changes are calculated with the ESMF SDK. */
   PLANNING,
   /** The changed files are backed up and written. */
   WRITING
}
