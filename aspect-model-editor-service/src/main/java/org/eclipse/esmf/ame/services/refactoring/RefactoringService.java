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

package org.eclipse.esmf.ame.services.refactoring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.esmf.ame.exceptions.ReadOnlySourceException;
import org.eclipse.esmf.ame.exceptions.RefactoringException;
import org.eclipse.esmf.ame.exceptions.RefactoringInProgressException;
import org.eclipse.esmf.ame.services.file.FilePathResolver;
import org.eclipse.esmf.ame.services.models.refactoring.ChangedFile;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringHistoryEntry;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringPhase;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringPreview;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringProgress;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringRequest;
import org.eclipse.esmf.ame.services.workspace.DynamicResolutionStrategy;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService.WorkspaceFile;
import org.eclipse.esmf.aspectmodel.AspectLoadingException;
import org.eclipse.esmf.aspectmodel.AspectModelFile;
import org.eclipse.esmf.aspectmodel.edit.AspectChangeManager;
import org.eclipse.esmf.aspectmodel.edit.AspectChangeManagerConfig;
import org.eclipse.esmf.aspectmodel.edit.Change;
import org.eclipse.esmf.aspectmodel.edit.ChangeGroup;
import org.eclipse.esmf.aspectmodel.edit.ChangeReport;
import org.eclipse.esmf.aspectmodel.edit.ChangeReportFormatter;
import org.eclipse.esmf.aspectmodel.edit.ModelChangeException;
import org.eclipse.esmf.aspectmodel.edit.WriteConfig;
import org.eclipse.esmf.aspectmodel.edit.WriteResult;
import org.eclipse.esmf.aspectmodel.edit.change.ChangeRdfPrefixes;
import org.eclipse.esmf.aspectmodel.edit.change.CopyFileWithIncreasedNamespaceVersion;
import org.eclipse.esmf.aspectmodel.edit.change.IncreaseVersion;
import org.eclipse.esmf.aspectmodel.edit.change.MoveElementToExistingFile;
import org.eclipse.esmf.aspectmodel.edit.change.MoveElementToNewFile;
import org.eclipse.esmf.aspectmodel.edit.change.MoveElementToOtherNamespaceExistingFile;
import org.eclipse.esmf.aspectmodel.edit.change.MoveElementToOtherNamespaceNewFile;
import org.eclipse.esmf.aspectmodel.edit.change.MoveRenameAspectModelFile;
import org.eclipse.esmf.aspectmodel.edit.change.RenameElement;
import org.eclipse.esmf.aspectmodel.edit.change.RenameNamespace;
import org.eclipse.esmf.aspectmodel.edit.change.RenameUrn;
import org.eclipse.esmf.aspectmodel.loader.AspectModelLoader;
import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;
import org.eclipse.esmf.aspectmodel.urn.UrnSyntaxException;
import org.eclipse.esmf.metamodel.AspectModel;
import org.eclipse.esmf.metamodel.ModelElement;
import org.eclipse.esmf.metamodel.impl.DefaultNamespace;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs refactorings across all files of all model sources with the {@link AspectChangeManager} of the ESMF SDK, so
 * that references in other files are updated as well.
 * <p>
 * A refactoring can be previewed without changing anything. It is only applied if it changes no file of a read-only
 * source, and it is recorded by the {@link RefactoringHistoryService} so that it can be reverted.
 * </p>
 */
@Singleton
public class RefactoringService {
   private static final Logger LOG = LoggerFactory.getLogger( RefactoringService.class );
   private static final AspectChangeManagerConfig CHANGE_MANAGER_CONFIG = new AspectChangeManagerConfig( List.of(), false );
   private static final int PREVIEW_STEPS = 2;
   private static final int APPLY_STEPS = 3;

   private final AspectModelLoader aspectModelLoader;
   private final WorkspaceReferenceService workspaceReferenceService;
   private final FilePathResolver filePathResolver;
   private final RefactoringHistoryService historyService;
   private final DynamicResolutionStrategy resolutionStrategy;
   private final ReentrantLock lock = new ReentrantLock();
   private final RefactoringProgressTracker progressTracker = new RefactoringProgressTracker();

   public RefactoringService( final AspectModelLoader aspectModelLoader, final WorkspaceReferenceService workspaceReferenceService,
         final FilePathResolver filePathResolver, final RefactoringHistoryService historyService,
         final DynamicResolutionStrategy resolutionStrategy ) {
      this.aspectModelLoader = aspectModelLoader;
      this.workspaceReferenceService = workspaceReferenceService;
      this.filePathResolver = filePathResolver;
      this.historyService = historyService;
      this.resolutionStrategy = resolutionStrategy;
   }

   /**
    * Calculates which files a refactoring changes without changing anything.
    *
    * @param request the refactoring
    * @return the files that would be created, modified and removed
    * @throws IllegalArgumentException if the request is incomplete or invalid
    * @throws NoSuchElementException if the element or file to refactor does not exist
    * @throws RefactoringException if the refactoring is not possible in the current state of the workspaces
    */
   public RefactoringPreview preview( final RefactoringRequest request ) {
      return progressTracker.track( PREVIEW_STEPS, () -> plan( request ).preview() );
   }

   /**
    * Applies a refactoring and records it, so that it can be reverted.
    *
    * @param request the refactoring
    * @return the recorded refactoring
    * @throws ReadOnlySourceException if the refactoring changes files of a read-only source
    * @throws RefactoringInProgressException if another refactoring is running
    * @see #preview(RefactoringRequest)
    */
   public RefactoringHistoryEntry apply( final RefactoringRequest request ) {
      return exclusively( () -> progressTracker.track( APPLY_STEPS, () -> {
         final PlannedRefactoring refactoring = plan( request );
         final RefactoringPreview preview = refactoring.preview();
         if ( !preview.blocked().isEmpty() ) {
            throw new ReadOnlySourceException( "The refactoring changes files of read-only sources: "
                  + String.join( ", ", preview.blocked().stream().map( ChangedFile::path ).toList() ) );
         }
         if ( !preview.applicable() ) {
            throw new RefactoringException( "The refactoring does not change any file." );
         }

         progressTracker.enter( RefactoringPhase.WRITING,
               preview.created().size() + preview.modified().size() + preview.removed().size() );
         final RefactoringHistoryEntry entry = historyService.record( preview.description(), request, preview.created(),
               preview.modified(), preview.removed(), () -> write( refactoring.changeManager(), preview.removed() ) );
         resolutionStrategy.invalidate();
         LOG.info( "Applied refactoring '{}'", preview.description() );
         return entry;
      } ) );
   }

   /**
    * Returns the progress of the latest running preview or refactoring.
    *
    * @return the progress, or {@link RefactoringProgress#IDLE} if nothing is running
    */
   public RefactoringProgress getProgress() {
      return progressTracker.current();
   }

   /**
    * Lists the refactorings that can be reverted, the latest first.
    *
    * @return the refactoring history
    */
   public List<RefactoringHistoryEntry> getHistory() {
      return historyService.getHistory();
   }

   /**
    * Reverts a refactoring.
    *
    * @param id the id of the refactoring
    * @param force whether to revert even if affected files were changed after the refactoring
    * @return the reverted refactoring
    * @throws RefactoringInProgressException if another refactoring is running
    * @see RefactoringHistoryService#revert(String, boolean)
    */
   public RefactoringHistoryEntry revert( final String id, final boolean force ) {
      return exclusively( () -> {
         final RefactoringHistoryEntry entry = historyService.revert( id, force );
         resolutionStrategy.invalidate();
         return entry;
      } );
   }

   private <T> T exclusively( final Supplier<T> action ) {
      if ( !lock.tryLock() ) {
         throw new RefactoringInProgressException();
      }
      try {
         return action.get();
      } finally {
         lock.unlock();
      }
   }

   private PlannedRefactoring plan( final RefactoringRequest request ) {
      if ( request.type() == null ) {
         throw new IllegalArgumentException( "The type of the refactoring is missing" );
      }
      final Workspace workspace = loadWorkspace();
      final RefactoringPlanner planner = new RefactoringPlanner( workspace, request );
      final Change change = switch ( request.type() ) {
         case RENAME_ELEMENT -> planner.renameElement();
         case MOVE_ELEMENT -> planner.moveElement();
         case RENAME_NAMESPACE -> planner.renameNamespace();
         case NEW_VERSION -> planner.newVersion();
         case RENAME_FILE -> planner.renameFile();
         case MOVE_FILE -> planner.moveFile();
      };

      progressTracker.enter( RefactoringPhase.PLANNING, workspace.files().size() );
      final AspectChangeManager changeManager = new AspectChangeManager( CHANGE_MANAGER_CONFIG, workspace.aspectModel() );
      final ChangeReport report;
      try {
         report = changeManager.applyChange( change );
      } catch ( final ModelChangeException | AspectLoadingException e ) {
         throw new RefactoringException( "The refactoring is not possible: " + e.getMessage(), e );
      }

      final List<ChangedFile> created = toChangedFiles( changeManager.createdFiles() );
      final List<ChangedFile> modified = toChangedFiles( changeManager.modifiedFiles() );
      final List<ChangedFile> removed = toChangedFiles( changeManager.removedFiles() );
      final List<ChangedFile> blocked = Stream.of( created, modified, removed ).flatMap( List::stream ).filter( ChangedFile::readOnly )
            .toList();
      final RefactoringPreview preview = new RefactoringPreview( planner.description(),
            ChangeReportFormatter.INSTANCE.apply( report, CHANGE_MANAGER_CONFIG ), created, modified, removed, blocked );
      return new PlannedRefactoring( preview, changeManager );
   }

   /**
    * Loads the files of all sources. If a file exists in several sources, the one of the first source is used, like
    * when resolving models.
    */
   private Workspace loadWorkspace() {
      final Map<String, WorkspaceFile> files = new LinkedHashMap<>();
      workspaceReferenceService.listWorkspaceFiles().forEach( file ->
            files.putIfAbsent( file.namespace() + "/" + file.version() + "/" + file.fileName(), file ) );
      progressTracker.enter( RefactoringPhase.LOADING, files.size() );
      try {
         final AspectModel aspectModel = aspectModelLoader.load( files.values().stream().map( file -> file.path().toFile() ).toList() );
         return new Workspace( aspectModel, List.copyOf( files.values() ) );
      } catch ( final RuntimeException e ) {
         throw new RefactoringException( "The workspaces cannot be refactored because they contain invalid files: " + e.getMessage(),
               e );
      }
   }

   private List<ChangedFile> toChangedFiles( final Stream<AspectModelFile> files ) {
      return files.map( file -> toChangedFile( Path.of( file.sourceUri() ).toAbsolutePath().normalize() ) )
            .sorted( Comparator.comparing( ChangedFile::path ) )
            .toList();
   }

   private ChangedFile toChangedFile( final Path file ) {
      final Path root = rootOf( file ).orElseThrow( () -> new RefactoringException( "The file is outside of the workspaces: " + file ) );
      final Path relative = root.relativize( file );
      if ( relative.getNameCount() != 3 ) {
         throw new RefactoringException( "The file is not at the location <namespace>/<version>/<file>: " + file );
      }
      return new ChangedFile( relative.getName( 0 ).toString(), relative.getName( 1 ).toString(), relative.getName( 2 ).toString(),
            file.toString(), workspaceReferenceService.isReadOnly( file ) );
   }

   private Optional<Path> rootOf( final Path file ) {
      return workspaceReferenceService.getReadableRoots().stream()
            .map( root -> root.toAbsolutePath().normalize() )
            .filter( file::startsWith )
            .findFirst();
   }

   private static void write( final AspectChangeManager changeManager, final List<ChangedFile> removed ) {
      final WriteResult result = changeManager.writeChangesToDisk( new WriteConfig( false ) );
      final List<String> errors = switch ( result ) {
         case final WriteResult.Success success -> List.of();
         case final WriteResult.WriteFailure failure -> failure.errorMessages();
         case final WriteResult.PreconditionsNotMet preconditionsNotMet -> preconditionsNotMet.errorMessages();
      };
      if ( !errors.isEmpty() ) {
         throw new RefactoringException( "The refactoring could not be written: " + String.join( " ", errors ) );
      }
      for ( final ChangedFile file : removed ) {
         try {
            RefactoringHistoryService.deleteEmptyParents( Path.of( file.path() ) );
         } catch ( final IOException e ) {
            throw new UncheckedIOException( "Could not clean up the directory of " + file.path(), e );
         }
      }
   }

   private record Workspace( AspectModel aspectModel, List<WorkspaceFile> files ) {
   }

   private record PlannedRefactoring( RefactoringPreview preview, AspectChangeManager changeManager ) {
   }

   /**
    * Translates a {@link RefactoringRequest} into a {@link Change} of the ESMF SDK.
    */
   private final class RefactoringPlanner {
      private final Workspace workspace;
      private final RefactoringRequest request;
      private String description = "";

      private RefactoringPlanner( final Workspace workspace, final RefactoringRequest request ) {
         this.workspace = workspace;
         this.request = request;
      }

      String description() {
         return description;
      }

      Change renameElement() {
         final AspectModelUrn urn = elementUrn();
         final AspectModelUrn newUrn = urn( urn.getUrnPrefix() + required( request.newName(), "newName" ) );
         requireElementExists( urn );
         requireElementAbsent( newUrn );
         description = "Rename " + urn.getName() + " to " + newUrn.getName();
         return new RenameElement( urn, newUrn.getName() );
      }

      Change moveElement() {
         final AspectModelUrn urn = elementUrn();
         final AspectModelFile sourceFile = definingFile( urn );
         final Path sourcePath = Path.of( sourceFile.sourceUri() );
         final String targetNamespace = Optional.ofNullable( request.targetNamespace() ).orElse( urn.getNamespaceMainPart() );
         final String targetVersion = Optional.ofNullable( request.targetVersion() ).orElse( urn.getVersion() );
         final String targetFileName = required( request.targetFileName(), "targetFileName" );
         final AspectModelUrn targetNamespaceUrn = namespaceUrn( targetNamespace, targetVersion );
         final boolean sameNamespace = targetNamespaceUrn.equals( namespaceUrn( urn.getNamespaceMainPart(), urn.getVersion() ) );
         if ( !sameNamespace ) {
            requireElementAbsent( urn( targetNamespaceUrn.getUrnPrefix() + urn.getName() ) );
         }

         final Optional<Path> existingTarget = request.targetWorkspace() == null
               ? findFile( targetNamespace, targetVersion, targetFileName, null )
               : findFile( targetNamespace, targetVersion, targetFileName, request.targetWorkspace() );
         final Path targetPath = existingTarget.orElseGet( () -> filePathResolver.resolveModelFile(
               request.targetWorkspace() == null ? rootOf( sourcePath ).orElseThrow() : workspaceReferenceService.resolveWorkspace(
                     request.targetWorkspace() ), targetNamespace, targetVersion, targetFileName ) );
         if ( targetPath.equals( sourcePath.toAbsolutePath().normalize() ) ) {
            throw new IllegalArgumentException( "The element is already defined in " + targetFileName );
         }

         description = "Move " + urn.getName() + " to " + targetNamespace + ":" + targetVersion + "/" + targetFileName;
         final URI target = targetPath.toUri();
         if ( sameNamespace ) {
            return existingTarget.isPresent()
                  ? new MoveElementToExistingFile( urn, target )
                  : new MoveElementToNewFile( urn, sourceFile.headerComment(), target );
         }
         final DefaultNamespace namespace = new DefaultNamespace( targetNamespaceUrn, List.of(), Optional.empty() );
         return existingTarget.isPresent()
               ? new MoveElementToOtherNamespaceExistingFile( urn, target, namespace )
               : new ChangeGroup(
                     new MoveElementToOtherNamespaceNewFile( urn, namespace, sourceFile.headerComment(), target ),
                     new ChangeRdfPrefixes( target, Map.of( "", targetNamespaceUrn.getUrnPrefix() ) ) );
      }

      Change renameNamespace() {
         final String namespace = required( request.namespace(), "namespace" );
         final String version = required( request.version(), "version" );
         final String targetNamespace = required( request.targetNamespace(), "targetNamespace" );
         final String targetVersion = required( request.targetVersion(), "targetVersion" );
         final AspectModelUrn from = namespaceUrn( namespace, version );
         final AspectModelUrn to = namespaceUrn( targetNamespace, targetVersion );
         if ( from.equals( to ) ) {
            throw new IllegalArgumentException( "The namespace is unchanged" );
         }
         final List<WorkspaceFile> namespaceFiles = filesOf( namespace, version, null );

         final List<Change> changes = new ArrayList<>();
         final Map<URI, URI> locations = new LinkedHashMap<>();
         for ( final WorkspaceFile file : namespaceFiles ) {
            final Path target = requireAbsent(
                  filePathResolver.resolveModelFile( file.root(), targetNamespace, targetVersion, file.fileName() ) );
            locations.put( file.path().toUri(), target.toUri() );
            changes.add( new MoveRenameAspectModelFile( file.path().toUri(), target.toUri() ) );
         }
         changes.add( new RenameNamespace( from, to ) );
         workspace.aspectModel().files().stream()
               .filter( file -> file.sourceModel().getNsPrefixMap().containsValue( from.getUrnPrefix() ) )
               .forEach( file -> {
                  final URI location = normalized( file.sourceUri() );
                  final Map<String, String> prefixes = new LinkedHashMap<>();
                  file.sourceModel().getNsPrefixMap().forEach( ( prefix, uri ) -> {
                     if ( uri.equals( from.getUrnPrefix() ) ) {
                        prefixes.put( prefix, to.getUrnPrefix() );
                     }
                  } );
                  changes.add( new ChangeRdfPrefixes( locations.getOrDefault( location, location ), prefixes ) );
               } );

         description = "Rename namespace " + namespace + ":" + version + " to " + targetNamespace + ":" + targetVersion;
         return new ChangeGroup( description, changes );
      }

      Change newVersion() {
         final String namespace = required( request.namespace(), "namespace" );
         final String version = required( request.version(), "version" );
         final IncreaseVersion increase = request.increase();
         if ( increase == null ) {
            throw new IllegalArgumentException( "The value increase is missing" );
         }
         final List<WorkspaceFile> files = filesOf( namespace, version, request.source() ).stream()
               .filter( file -> request.fileName() == null || file.fileName().equals( request.fileName() ) )
               .toList();
         if ( files.isEmpty() ) {
            throw new NoSuchElementException( "No Aspect Model file found in " + namespace + ":" + version );
         }

         description = "Create new " + increase.name().toLowerCase( Locale.ROOT ) + " version of " + namespace + ":" + version
               + ( request.fileName() == null ? "" : "/" + request.fileName() );
         return new ChangeGroup( description, files.stream()
               .<Change>map( file -> new CopyFileWithIncreasedNamespaceVersion( file.path().toUri(), increase ) )
               .toList() );
      }

      Change renameFile() {
         final String namespace = required( request.namespace(), "namespace" );
         final String version = required( request.version(), "version" );
         final String fileName = required( request.fileName(), "fileName" );
         final String newName = required( request.newName(), "newName" );
         final WorkspaceFile file = fileOf( namespace, version, fileName );
         final Path target = filePathResolver.resolveModelFile( file.root(), namespace, version, newName );
         if ( target.equals( file.path() ) ) {
            throw new IllegalArgumentException( "The file name is unchanged" );
         }
         requireAbsent( target );

         description = "Rename " + fileName + " to " + newName;
         return new MoveRenameAspectModelFile( file.path().toUri(), target.toUri() );
      }

      /**
       * Moves a file into another namespace version: its elements get URNs of the target namespace, all references to them are
       * updated and the files using them get a prefix for the target namespace.
       */
      Change moveFile() {
         final String namespace = required( request.namespace(), "namespace" );
         final String version = required( request.version(), "version" );
         final String fileName = required( request.fileName(), "fileName" );
         final String targetNamespace = required( request.targetNamespace(), "targetNamespace" );
         final String targetVersion = required( request.targetVersion(), "targetVersion" );
         final String targetFileName = Optional.ofNullable( request.targetFileName() ).filter( name -> !name.isBlank() ).orElse( fileName );
         final AspectModelUrn from = namespaceUrn( namespace, version );
         final AspectModelUrn to = namespaceUrn( targetNamespace, targetVersion );
         if ( from.equals( to ) && targetFileName.equals( fileName ) ) {
            throw new IllegalArgumentException( "The namespace and the name of the file are unchanged" );
         }
         final WorkspaceFile file = fileOf( namespace, version, fileName );
         final URI target = requireAbsent(
               filePathResolver.resolveModelFile( file.root(), targetNamespace, targetVersion, targetFileName ) ).toUri();

         description = "Move " + fileName + " to " + targetNamespace + ":" + targetVersion + "/" + targetFileName;
         final List<Change> changes = new ArrayList<>();
         changes.add( new MoveRenameAspectModelFile( file.path().toUri(), target ) );
         if ( from.equals( to ) ) {
            return new ChangeGroup( description, changes );
         }

         final AspectModelFile movedFile = aspectModelFile( file.path() );
         final Map<String, AspectModelUrn> renamed = new LinkedHashMap<>();
         movedFile.elements().stream()
               .filter( element -> !element.isAnonymous() && element.urn().getUrnPrefix().equals( from.getUrnPrefix() ) )
               .map( ModelElement::urn )
               .forEach( urn -> {
                  final AspectModelUrn newUrn = urn( to.getUrnPrefix() + urn.getName() );
                  requireElementAbsent( newUrn );
                  renamed.put( urn.toString(), newUrn );
               } );
         renamed.forEach( ( urn, newUrn ) -> changes.add( new RenameUrn( urn( urn ), newUrn ) ) );
         changes.add( new ChangeRdfPrefixes( target, movedFilePrefixes( movedFile, from, to, renamed.keySet() ) ) );
         workspace.aspectModel().files().stream()
               .filter( other -> other != movedFile && other.sourceLocation().isPresent() )
               .filter( other -> !other.sourceModel().getNsPrefixMap().containsValue( to.getUrnPrefix() ) )
               .filter( other -> referencedUris( other.sourceModel() ).anyMatch( renamed::containsKey ) )
               .forEach( other -> changes.add( new ChangeRdfPrefixes( normalized( other.sourceUri() ),
                     Map.of( uniquePrefix( other.sourceModel(), targetNamespace ), to.getUrnPrefix() ) ) ) );
         return new ChangeGroup( description, changes );
      }

      /**
       * The prefixes of the old namespace point to the target namespace in the moved file. If it still references elements that
       * remain in the old namespace, it gets a new prefix for the old namespace.
       */
      private static Map<String, String> movedFilePrefixes( final AspectModelFile movedFile, final AspectModelUrn from,
            final AspectModelUrn to, final Set<String> movedUrns ) {
         final Model model = movedFile.sourceModel();
         final Map<String, String> prefixes = new LinkedHashMap<>();
         model.getNsPrefixMap().forEach( ( prefix, uri ) -> {
            if ( uri.equals( from.getUrnPrefix() ) ) {
               prefixes.put( prefix, to.getUrnPrefix() );
            }
         } );
         if ( prefixes.isEmpty() ) {
            prefixes.put( uniquePrefix( model, to.getNamespaceMainPart() ), to.getUrnPrefix() );
         }
         final boolean referencesOldNamespace = referencedUris( model )
               .anyMatch( uri -> uri.startsWith( from.getUrnPrefix() ) && !movedUrns.contains( uri ) );
         if ( referencesOldNamespace ) {
            prefixes.put( uniquePrefix( model, from.getNamespaceMainPart() ), from.getUrnPrefix() );
         }
         return prefixes;
      }

      /** A prefix derived from the last segment of the namespace that is not used in the model yet, e.g. {@code example2}. */
      private static String uniquePrefix( final Model model, final String namespace ) {
         final String segment = namespace.substring( namespace.lastIndexOf( '.' ) + 1 ).replaceAll( "[^A-Za-z0-9_-]", "" );
         final String base = segment.isEmpty() || !Character.isLetter( segment.charAt( 0 ) ) ? "ns" : segment;
         String prefix = base;
         for ( int i = 2; model.getNsPrefixURI( prefix ) != null; i++ ) {
            prefix = base + i;
         }
         return prefix;
      }

      private static Stream<String> referencedUris( final Model model ) {
         return model.listStatements().toList().stream()
               .flatMap( statement -> Stream.of( statement.getSubject(), statement.getPredicate(), statement.getObject() ) )
               .filter( RDFNode::isURIResource )
               .map( node -> node.asResource().getURI() );
      }

      private WorkspaceFile fileOf( final String namespace, final String version, final String fileName ) {
         return filesOf( namespace, version, request.source() ).stream()
               .filter( candidate -> candidate.fileName().equals( fileName ) )
               .findFirst()
               .orElseThrow( () -> new NoSuchElementException( "Aspect Model file not found: " + namespace + ":" + version + "/" + fileName ) );
      }

      private AspectModelFile aspectModelFile( final Path path ) {
         final URI location = normalized( path.toUri() );
         return workspace.aspectModel().files().stream()
               .filter( file -> file.sourceLocation().isPresent() && normalized( file.sourceUri() ).equals( location ) )
               .findFirst()
               .orElseThrow( () -> new NoSuchElementException( "Aspect Model file not loaded: " + path ) );
      }

      private AspectModelUrn elementUrn() {
         final AspectModelUrn urn = urn( required( request.urn(), "urn" ) );
         if ( urn.getName().isEmpty() ) {
            throw new IllegalArgumentException( "The URN does not identify a model element: " + request.urn() );
         }
         return urn;
      }

      private void requireElementExists( final AspectModelUrn urn ) {
         definingFile( urn );
      }

      private void requireElementAbsent( final AspectModelUrn urn ) {
         if ( findDefiningFile( urn ).isPresent() ) {
            throw new RefactoringException( "An element " + urn + " exists already." );
         }
      }

      private static Path requireAbsent( final Path file ) {
         if ( Files.exists( file ) ) {
            throw new RefactoringException( "The file exists already: " + file );
         }
         return file;
      }

      private AspectModelFile definingFile( final AspectModelUrn urn ) {
         return findDefiningFile( urn ).orElseThrow( () -> new NoSuchElementException( "Model element not found: " + urn ) );
      }

      private Optional<AspectModelFile> findDefiningFile( final AspectModelUrn urn ) {
         return workspace.aspectModel().files().stream()
               .filter( file -> file.elements().stream().filter( element -> !element.isAnonymous() ).map( ModelElement::urn )
                     .anyMatch( urn::equals ) )
               .findFirst();
      }

      private List<WorkspaceFile> filesOf( final String namespace, final String version, final @Nullable String source ) {
         final Optional<Path> root = Optional.ofNullable( source ).filter( value -> !value.isBlank() )
               .map( value -> Path.of( value ).toAbsolutePath().normalize() );
         final List<WorkspaceFile> files = workspace.files().stream()
               .filter( file -> file.namespace().equals( namespace ) && file.version().equals( version ) )
               .filter( file -> root.map( file.root()::equals ).orElse( true ) )
               .toList();
         if ( files.isEmpty() ) {
            throw new NoSuchElementException( "Namespace not found: " + namespace + ":" + version );
         }
         return files;
      }

      private Optional<Path> findFile( final String namespace, final String version, final String fileName,
            final @Nullable String source ) {
         final Optional<Path> root = Optional.ofNullable( source ).map( workspaceReferenceService::resolveWorkspace )
               .map( path -> path.toAbsolutePath().normalize() );
         return workspace.files().stream()
               .filter( file -> file.namespace().equals( namespace ) && file.version().equals( version )
                     && file.fileName().equals( fileName ) )
               .filter( file -> root.map( file.root()::equals ).orElse( true ) )
               .map( WorkspaceFile::path )
               .findFirst();
      }

      private static URI normalized( final URI location ) {
         return Path.of( location ).toAbsolutePath().normalize().toUri();
      }

      private static String required( final @Nullable String value, final String name ) {
         if ( value == null || value.isBlank() ) {
            throw new IllegalArgumentException( "The value " + name + " is missing" );
         }
         return value.trim();
      }

      private static AspectModelUrn namespaceUrn( final String namespace, final String version ) {
         return urn( "urn:samm:" + namespace + ":" + version );
      }

      private static AspectModelUrn urn( final String urn ) {
         try {
            return AspectModelUrn.fromUrn( urn );
         } catch ( final UrnSyntaxException e ) {
            throw new IllegalArgumentException( "Invalid URN: " + urn, e );
         }
      }
   }
}
