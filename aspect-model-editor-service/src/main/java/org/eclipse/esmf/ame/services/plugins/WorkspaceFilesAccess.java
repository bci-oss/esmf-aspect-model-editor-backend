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

package org.eclipse.esmf.ame.services.plugins;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import org.eclipse.esmf.ame.plugin.api.WorkspaceAccess;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService.WorkspaceFile;

import jakarta.inject.Singleton;

/**
 * Gives plugins read access to the model files of all sources. A file existing in several sources is listed once, as
 * the file of the first source in resolution order, the same way the editor resolves it.
 */
@Singleton
public class WorkspaceFilesAccess implements WorkspaceAccess {
   private final WorkspaceReferenceService workspaceReferenceService;

   public WorkspaceFilesAccess( final WorkspaceReferenceService workspaceReferenceService ) {
      this.workspaceReferenceService = workspaceReferenceService;
   }

   @Override
   public List<ModelFile> files() {
      return workspaceReferenceService.listWorkspaceFiles().stream().map( WorkspaceFilesAccess::toModelFile ).distinct().toList();
   }

   @Override
   public Optional<String> read( final ModelFile file ) {
      return workspaceReferenceService.listWorkspaceFiles().stream()
            .filter( candidate -> toModelFile( candidate ).equals( file ) )
            .findFirst()
            .map( WorkspaceFilesAccess::readContent );
   }

   private static ModelFile toModelFile( final WorkspaceFile file ) {
      return new ModelFile( file.namespace(), file.version(), file.fileName() );
   }

   private static String readContent( final WorkspaceFile file ) {
      try {
         return Files.readString( file.path(), StandardCharsets.UTF_8 );
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not read " + file.path(), e );
      }
   }
}
