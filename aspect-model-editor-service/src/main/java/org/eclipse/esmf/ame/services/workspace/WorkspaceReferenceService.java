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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.constants.ApplicationConstants;
import org.eclipse.esmf.ame.model.ModelReference;
import org.eclipse.esmf.ame.model.ReferenceReport;
import org.eclipse.esmf.ame.model.UnreadableModelFile;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the workspace files that use the elements of a namespace or of a single Aspect Model file.
 * <p>
 * Only incoming references count: a file that uses elements of the namespace/file to be deleted. Elements that
 * another remaining file defines as well stay available and therefore do not count. Every workspace
 * file is read as plain RDF (without resolving its imports), so broken or outdated models are checked as well.
 * Files that cannot be read at all are reported separately because they might contain references.
 */
@Singleton
public class WorkspaceReferenceService {
   private static final Logger LOG = LoggerFactory.getLogger( WorkspaceReferenceService.class );

   private final Path modelPath;
   private final @Nullable WorkspaceConfigService workspaceConfigService;

   @Inject
   public WorkspaceReferenceService( final Path modelPath, final @Nullable WorkspaceConfigService workspaceConfigService ) {
      this.modelPath = modelPath;
      this.workspaceConfigService = workspaceConfigService;
   }

   public WorkspaceReferenceService( final Path modelPath ) {
      this( modelPath, null );
   }

   /**
    * Returns the default workspace directory.
    *
    * @return the default workspace directory
    */
   public Path getDefaultModelPath() {
      return workspaceConfigService != null ? workspaceConfigService.getDefaultWorkspacePath() : modelPath;
   }

   /**
    * Returns the root directories of all model sources in resolution order, including read-only sources.
    *
    * @return the readable roots
    */
   public List<Path> getReadableRoots() {
      return workspaceConfigService != null ? workspaceConfigService.getReadablePaths() : List.of( modelPath );
   }

   /**
    * Returns the writable workspace directories.
    *
    * @return the writable roots
    */
   public List<Path> getWritableRoots() {
      return workspaceConfigService != null ? workspaceConfigService.getWorkspacePaths() : List.of( modelPath );
   }

   /**
    * Resolves a writable workspace.
    *
    * @param workspace the workspace path, {@code null} or blank for the active workspace
    * @return the workspace directory
    * @throws IllegalArgumentException if the workspace is not registered
    */
   public Path resolveWorkspace( final @Nullable String workspace ) {
      if ( workspaceConfigService != null ) {
         return workspaceConfigService.resolveTargetWorkspace( workspace );
      }
      if ( workspace == null || workspace.isBlank() || Path.of( workspace.trim() ).toAbsolutePath().normalize()
            .equals( modelPath.toAbsolutePath().normalize() ) ) {
         return modelPath;
      }
      throw new IllegalArgumentException( "Workspace is not registered: " + workspace );
   }

   /**
    * Whether a file belongs to a read-only source.
    *
    * @param file the file
    * @return {@code true} if the file must not be changed
    */
   public boolean isReadOnly( final Path file ) {
      return workspaceConfigService != null && workspaceConfigService.isReadOnly( file );
   }

   /**
    * A Turtle file at the standard location {@code <root>/<namespace>/<version>/<file>.ttl}.
    */
   public record WorkspaceFile( Path path, String namespace, String version, String fileName, Path root ) {
      boolean isIn( final String otherNamespace, final String otherVersion ) {
         return namespace.equals( otherNamespace ) && version.equals( otherVersion );
      }
   }

   /**
    * Lists the Turtle files of all model sources at their standard location, in resolution order of the sources and
    * sorted by namespace, version and name within each source. Other files (e.g. backups) are ignored.
    *
    * @return the files of all sources
    */
   public List<WorkspaceFile> listWorkspaceFiles() {
      return getReadableRoots().stream().flatMap( root -> listWorkspaceFiles( root ).stream() ).toList();
   }

   /**
    * Lists the Turtle files of one source directory at their standard location, sorted by namespace, version and
    * name. Other files (e.g. backups) are ignored.
    *
    * @param root the source directory
    * @return the files of the source
    */
   public List<WorkspaceFile> listWorkspaceFiles( final Path root ) {
      final Path workspace = root.toAbsolutePath().normalize();
      if ( !Files.isDirectory( workspace ) ) {
         return List.of();
      }
      try ( final Stream<Path> paths = Files.walk( workspace, 3 ) ) {
         return paths.filter( Files::isRegularFile )
               .filter(
                     path -> path.getFileName().toString().toLowerCase( Locale.ROOT ).endsWith( ApplicationConstants.FileExtensions.TTL ) )
               .map( workspace::relativize )
               .filter( relative -> relative.getNameCount() == 3 )
               .map( relative -> new WorkspaceFile( workspace.resolve( relative ), relative.getName( 0 ).toString(),
                     relative.getName( 1 ).toString(), relative.getName( 2 ).toString(), workspace ) )
               .sorted( Comparator.comparing( WorkspaceFile::namespace ).thenComparing( WorkspaceFile::version )
                     .thenComparing( WorkspaceFile::fileName ) )
               .toList();
      } catch ( final IOException e ) {
         throw new UncheckedIOException( "Could not list the workspace files", e );
      }
   }

   /**
    * Finds the files of other namespaces (or other versions of the same namespace) in all sources that use elements
    * of the given namespace version.
    *
    * @param namespace the namespace, e.g. {@code org.eclipse.example}
    * @param version the version, e.g. {@code 1.0.0}
    * @return the references found
    */
   public ReferenceReport findNamespaceReferences( final String namespace, final String version ) {
      return findNamespaceReferences( null, namespace, version );
   }

   /**
    * Finds the files that use elements of the given namespace version of one source. Files of the same namespace
    * version in other sources are checked as well, since they stay after the deletion.
    *
    * @param root the source directory containing the namespace version to delete, {@code null} for all sources
    * @param namespace the namespace, e.g. {@code org.eclipse.example}
    * @param version the version, e.g. {@code 1.0.0}
    * @return the references found
    */
   public ReferenceReport findNamespaceReferences( final @Nullable Path root, final String namespace, final String version ) {
      final String elementPrefix = "urn:samm:" + namespace + ":" + version + "#";
      final Path normalizedRoot = root == null ? null : root.toAbsolutePath().normalize();
      final List<WorkspaceFile> others = listWorkspaceFiles().stream()
            .filter( file -> !file.isIn( namespace, version ) || ( normalizedRoot != null && !file.root().equals( normalizedRoot ) ) )
            .toList();
      return findReferences( others, uri -> uri.startsWith( elementPrefix ) );
   }

   /**
    * Finds the other workspace files (of any namespace) that use elements defined in the given file.
    *
    * @param file the Aspect Model file to be deleted
    * @return the references found
    */
   public ReferenceReport findFileReferences( final Path file ) {
      final Path target = file.toAbsolutePath().normalize();
      final Set<String> definedElements = definedUrisOrEmpty( target );
      final List<WorkspaceFile> others = listWorkspaceFiles().stream()
            .filter( other -> !other.path().toAbsolutePath().normalize().equals( target ) )
            .toList();
      return findReferences( others, definedElements::contains );
   }

   /**
    * Finds the file of a namespace version that defines the given element. The files are read as plain RDF (without
    * resolving their imports), so a file is found even if elements it references are missing in the workspace.
    *
    * @param namespace the namespace, e.g. {@code org.eclipse.example}
    * @param version the version, e.g. {@code 1.0.0}
    * @param elementUri the URN of the element, e.g. {@code urn:samm:org.eclipse.example:1.0.0#property}
    * @return the defining file, empty if no file of the namespace version defines the element
    * @throws RuntimeException the error of the first unreadable file if no file defines the element, since the
    *       unreadable file might define it
    */
   public Optional<Path> findDefiningFile( final String namespace, final String version, final String elementUri ) {
      RuntimeException firstReadError = null;
      final List<WorkspaceFile> candidates = listWorkspaceFiles().stream().filter( file -> file.isIn( namespace, version ) ).toList();
      for ( final WorkspaceFile file : candidates ) {
         try {
            if ( definedUris( parse( file.path() ) ).contains( elementUri ) ) {
               return Optional.of( file.path() );
            }
         } catch ( final RuntimeException e ) {
            LOG.warn( "Could not read {} while looking for {}: {}", file.path(), elementUri, e.getMessage() );
            if ( firstReadError == null ) {
               firstReadError = e;
            }
         }
      }
      if ( firstReadError != null ) {
         throw firstReadError;
      }
      return Optional.empty();
   }

   private ReferenceReport findReferences( final List<WorkspaceFile> files, final Predicate<String> isTargetElement ) {
      final Map<WorkspaceFile, Model> models = new LinkedHashMap<>();
      final List<UnreadableModelFile> unreadable = new ArrayList<>();
      for ( final WorkspaceFile file : files ) {
         try {
            models.put( file, parse( file.path() ) );
         } catch ( final RuntimeException e ) {
            LOG.warn( "Could not check references in {}: {}", file.path(), e.getMessage() );
            unreadable.add( new UnreadableModelFile( file.namespace(), file.version(), file.fileName(),
                  e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage() ) );
         }
      }

      // Elements that remaining files define themselves are still available after the deletion.
      final Set<String> stillDefined = new HashSet<>();
      models.values().forEach( model -> stillDefined.addAll( definedUris( model ) ) );
      final Predicate<String> isRemovedElement = isTargetElement.and( uri -> !stillDefined.contains( uri ) );

      final List<ModelReference> references = new ArrayList<>();
      models.forEach( ( file, model ) -> {
         final Set<String> usedElements = usedUris( model, isRemovedElement );
         if ( !usedElements.isEmpty() ) {
            references.add( new ModelReference( file.namespace(), file.version(), file.fileName(), List.copyOf( usedElements ) ) );
         }
      } );
      return ReferenceReport.of( references, unreadable );
   }

   private static Set<String> definedUrisOrEmpty( final Path file ) {
      try {
         return definedUris( parse( file ) );
      } catch ( final RuntimeException e ) {
         // Elements of an unreadable file are unknown; files using them are broken already.
         LOG.warn( "Could not read the file to delete {}: {}", file, e.getMessage() );
         return Set.of();
      }
   }

   /**
    * The elements a file defines itself: named resources with an {@code rdf:type}.
    */
   private static Set<String> definedUris( final Model model ) {
      final Set<String> result = new TreeSet<>();
      model.listResourcesWithProperty( RDF.type ).forEachRemaining( resource -> {
         if ( resource.isURIResource() ) {
            result.add( resource.getURI() );
         }
      } );
      return result;
   }

   private static Set<String> usedUris( final Model model, final Predicate<String> isRemovedElement ) {
      final Set<String> result = new TreeSet<>();
      model.listStatements().forEachRemaining( statement ->
            Stream.of( statement.getSubject(), statement.getPredicate(), statement.getObject() )
                  .filter( RDFNode::isURIResource )
                  .map( node -> node.asResource().getURI() )
                  .filter( isRemovedElement )
                  .forEach( result::add ) );
      return result;
   }

   private static Model parse( final Path file ) {
      final Model model = ModelFactory.createDefaultModel();
      RDFParser.source( file ).lang( Lang.TURTLE ).parse( model );
      return model;
   }
}
