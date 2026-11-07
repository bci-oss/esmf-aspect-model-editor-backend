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

import org.eclipse.esmf.aspectmodel.edit.change.IncreaseVersion;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Describes a refactoring. Which values are required depends on the {@link RefactoringType}:
 * <ul>
 *    <li>{@link RefactoringType#RENAME_ELEMENT}: {@code urn}, {@code newName}</li>
 *    <li>{@link RefactoringType#MOVE_ELEMENT}: {@code urn}, {@code targetFileName}, optionally {@code targetNamespace} and
 *    {@code targetVersion} (default: the namespace of the element) and {@code targetWorkspace} for a new file</li>
 *    <li>{@link RefactoringType#RENAME_NAMESPACE}: {@code namespace}, {@code version}, {@code targetNamespace},
 *    {@code targetVersion}</li>
 *    <li>{@link RefactoringType#NEW_VERSION}: {@code namespace}, {@code version}, {@code increase}, optionally
 *    {@code fileName} to copy a single file only</li>
 *    <li>{@link RefactoringType#RENAME_FILE}: {@code namespace}, {@code version}, {@code fileName}, {@code newName}</li>
 *    <li>{@link RefactoringType#MOVE_FILE}: {@code namespace}, {@code version}, {@code fileName}, {@code targetNamespace},
 *    {@code targetVersion}, optionally {@code targetFileName} (default: the same file name)</li>
 * </ul>
 *
 * @param type the kind of refactoring
 * @param urn the URN of the element to rename or move
 * @param newName the new element name or file name
 * @param namespace the namespace of the namespace version or file to change, e.g. {@code org.eclipse.example}
 * @param version the version of the namespace version or file to change, e.g. {@code 1.0.0}
 * @param fileName the name of the file to change, e.g. {@code Movement.ttl}
 * @param source the root directory of the source containing the file, {@code null} to search all sources
 * @param targetNamespace the new namespace
 * @param targetVersion the new version
 * @param targetFileName the file to move an element to; it is created if it does not exist. The new name of a moved file.
 * @param targetWorkspace the workspace a new file is created in, {@code null} for the workspace of the source file
 * @param increase the version part to increase
 */
@Serdeable
@Introspected
public record RefactoringRequest(
      RefactoringType type,
      @Nullable String urn,
      @Nullable String newName,
      @Nullable String namespace,
      @Nullable String version,
      @Nullable String fileName,
      @Nullable String source,
      @Nullable String targetNamespace,
      @Nullable String targetVersion,
      @Nullable String targetFileName,
      @Nullable String targetWorkspace,
      @Nullable IncreaseVersion increase
) {
}
