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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.model.UnreadableModelFile;
import org.eclipse.esmf.ame.services.models.workspace.ElementUsage;
import org.eclipse.esmf.ame.services.models.workspace.ElementUsageReport;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService.WorkspaceFile;
import org.eclipse.esmf.aspectmodel.AspectModelFile;
import org.eclipse.esmf.aspectmodel.resolver.AspectModelFileLoader;
import org.eclipse.esmf.aspectmodel.resolver.ModelSource;
import org.eclipse.esmf.aspectmodel.resolver.exceptions.ModelResolutionException;
import org.eclipse.esmf.aspectmodel.stats.Reference;
import org.eclipse.esmf.aspectmodel.stats.Usage;
import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;

import jakarta.inject.Singleton;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds where model elements are used in the files of all sources with {@link Usage} of the ESMF SDK. Within a file,
 * an element is also found if it uses the element indirectly, e.g. an Aspect using a Property with the Characteristic.
 * <p>
 * The files are read as plain RDF without resolving their references, so the usages are found even if some
 * references cannot be resolved. Files that cannot be read at all are skipped and reported.
 * </p>
 */
@Singleton
public class ElementUsageService {
   private static final Logger LOG = LoggerFactory.getLogger( ElementUsageService.class );

   private final WorkspaceReferenceService workspaceReferenceService;

   public ElementUsageService( final WorkspaceReferenceService workspaceReferenceService ) {
      this.workspaceReferenceService = workspaceReferenceService;
   }

   /**
    * Finds the elements using the given element.
    *
    * @param urn the URN of the element
    * @return the elements using it and the files that could not be checked
    * @throws NoSuchElementException if no file defines the element
    */
   public ElementUsageReport findUsages( final AspectModelUrn urn ) {
      final Map<AspectModelFile, WorkspaceFile> files = new LinkedHashMap<>();
      final List<UnreadableModelFile> unreadable = new ArrayList<>();
      for ( final WorkspaceFile file : resolvableFiles() ) {
         try {
            files.put( AspectModelFileLoader.load( file.path().toFile() ), file );
         } catch ( final RuntimeException e ) {
            LOG.warn( "Could not check usages in {}: {}", file.path(), e.getMessage() );
            unreadable.add( new UnreadableModelFile( file.namespace(), file.version(), file.fileName(),
                  e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage() ) );
         }
      }

      // An unreadable file might define the element, so it is only unknown if all files could be read.
      if ( unreadable.isEmpty() && files.keySet().stream().noneMatch( file -> defines( file, urn ) ) ) {
         throw new NoSuchElementException( "No file defines the element " + urn );
      }

      final List<Reference> references;
      try {
         references = new Usage( new LoadedModelSource( List.copyOf( files.keySet() ) ) ).referencesTo( urn );
      } catch ( final ModelResolutionException e ) {
         LOG.warn( "Could not determine the file defining {}: {}", urn, e.getMessage() );
         return new ElementUsageReport( urn.toString(), List.of(), unreadable );
      }

      final List<ElementUsage> usages = references.stream()
            .map( reference -> toElementUsage( reference, files.get( reference.pointerSource() ) ) )
            .distinct()
            .sorted( Comparator.comparing( ElementUsage::path ).thenComparing( ElementUsage::urn ) )
            .toList();
      return new ElementUsageReport( urn.toString(), usages, unreadable );
   }

   /**
    * The files used when resolving models: if a file exists in several sources, the one of the first source.
    */
   private List<WorkspaceFile> resolvableFiles() {
      final Map<String, WorkspaceFile> files = new LinkedHashMap<>();
      workspaceReferenceService.listWorkspaceFiles().forEach( file ->
            files.putIfAbsent( file.namespace() + "/" + file.version() + "/" + file.fileName(), file ) );
      return List.copyOf( files.values() );
   }

   private static boolean defines( final AspectModelFile file, final AspectModelUrn urn ) {
      return file.sourceModel().contains( file.sourceModel().createResource( urn.toString() ), RDF.type );
   }

   private ElementUsage toElementUsage( final Reference reference, final WorkspaceFile file ) {
      final Path path = file.path().toAbsolutePath().normalize();
      return new ElementUsage( reference.pointer().toString(), file.namespace(), file.version(), file.fileName(), path.toString(),
            workspaceReferenceService.isReadOnly( path ), reference.pointerSource() != reference.pointeeSource() );
   }

   /**
    * A {@link ModelSource} of files that have been loaded already.
    */
   private record LoadedModelSource( List<AspectModelFile> files ) implements ModelSource {
      @Override
      public Stream<URI> listContents() {
         return files.stream().map( AspectModelFile::sourceUri );
      }

      @Override
      public Stream<URI> listContentsForNamespace( final AspectModelUrn namespace ) {
         return loadContentsForNamespace( namespace ).map( AspectModelFile::sourceUri );
      }

      @Override
      public Stream<AspectModelFile> loadContents() {
         return files.stream();
      }

      @Override
      public Stream<AspectModelFile> loadContentsForNamespace( final AspectModelUrn namespace ) {
         return files.stream().filter( file -> file.namespaceUrn().equals( namespace ) );
      }
   }
}
