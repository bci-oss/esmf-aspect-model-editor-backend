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

package org.eclipse.esmf.ame.services.workspace;

import java.net.URI;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.esmf.aspectmodel.AspectModelFile;
import org.eclipse.esmf.aspectmodel.resolver.EitherStrategy;
import org.eclipse.esmf.aspectmodel.resolver.FileSystemStrategy;
import org.eclipse.esmf.aspectmodel.resolver.ResolutionStrategy;
import org.eclipse.esmf.aspectmodel.resolver.ResolutionStrategySupport;
import org.eclipse.esmf.aspectmodel.resolver.exceptions.ModelResolutionException;
import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;

import jakarta.inject.Singleton;

/**
 * A dynamic {@link ResolutionStrategy} that delegates resolution to all workspace paths
 * configured in {@link WorkspaceConfigService}.
 * <p>
 * When multiple workspace directories are registered, this strategy wraps them in an
 * {@link EitherStrategy}, allowing URN and file lookups across all directories seamlessly.
 * </p>
 */
@Singleton
public class DynamicResolutionStrategy implements ResolutionStrategy {

   private final WorkspaceConfigService workspaceConfigService;

   public DynamicResolutionStrategy( final WorkspaceConfigService workspaceConfigService ) {
      this.workspaceConfigService = workspaceConfigService;
   }

   protected ResolutionStrategy currentStrategy() {
      final List<Path> paths = workspaceConfigService.getWorkspacePaths();
      if ( paths.isEmpty() ) {
         return new FileSystemStrategy( workspaceConfigService.getDefaultWorkspacePath() );
      }
      if ( paths.size() == 1 ) {
         return new FileSystemStrategy( paths.getFirst() );
      }
      final List<ResolutionStrategy> strategies = paths.stream()
            .map( FileSystemStrategy::new )
            .map( ResolutionStrategy.class::cast )
            .toList();
      return new EitherStrategy( strategies );
   }

   @Override
   public AspectModelFile apply( final AspectModelUrn urn, final ResolutionStrategySupport support )
         throws ModelResolutionException {
      return currentStrategy().apply( urn, support );
   }

   @Override
   public Stream<URI> listContents() {
      return currentStrategy().listContents();
   }

   @Override
   public Stream<URI> listContentsForNamespace( final AspectModelUrn urn ) {
      return currentStrategy().listContentsForNamespace( urn );
   }

   @Override
   public Stream<AspectModelFile> loadContents() {
      return currentStrategy().loadContents();
   }

   @Override
   public Stream<AspectModelFile> loadContentsForNamespace( final AspectModelUrn urn ) {
      return currentStrategy().loadContentsForNamespace( urn );
   }

   @Override
   public Iterator<AspectModelFile> iterator() {
      return loadContents().iterator();
   }
}
