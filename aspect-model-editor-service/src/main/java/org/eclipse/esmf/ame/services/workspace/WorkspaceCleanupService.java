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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.esmf.ame.exceptions.FileNotFoundException;
import org.eclipse.esmf.ame.exceptions.ModelReferencedException;
import org.eclipse.esmf.ame.exceptions.ReadOnlySourceException;
import org.eclipse.esmf.ame.model.ReferenceReport;
import org.eclipse.esmf.ame.services.PackageService;
import org.eclipse.esmf.ame.services.file.FileOperations;
import org.eclipse.esmf.ame.services.file.FilePathResolver;
import org.eclipse.esmf.ame.services.models.ClearWorkspaceResult;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService.WorkspaceFile;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes complete namespaces or all Aspect Models of a workspace. Read-only sources are never changed.
 * <p>
 * Only Turtle files at the standard location {@code <namespace>/<version>/<file>.ttl} are deleted, together with
 * directories that are empty afterwards. Other files, e.g. workspace backups, are kept.
 */
@Singleton
public class WorkspaceCleanupService {
   private static final Logger LOG = LoggerFactory.getLogger( WorkspaceCleanupService.class );

   private final WorkspaceReferenceService referenceService;
   private final FileOperations fileOperations;
   private final FilePathResolver filePathResolver;
   private final PackageService packageService;

   public WorkspaceCleanupService( final WorkspaceReferenceService referenceService, final FileOperations fileOperations,
         final FilePathResolver filePathResolver, final PackageService packageService ) {
      this.referenceService = referenceService;
      this.fileOperations = fileOperations;
      this.filePathResolver = filePathResolver;
      this.packageService = packageService;
   }

   /**
    * Checks whether a namespace version can be deleted.
    *
    * @param namespace the namespace
    * @param version the version
    * @return the files of other namespaces or versions that use its elements
    * @throws IllegalArgumentException if namespace or version are invalid
    */
   public ReferenceReport checkNamespace( final String namespace, final String version ) {
      return checkNamespace( namespace, version, null );
   }

   /**
    * Checks whether a namespace version of a workspace can be deleted.
    *
    * @param namespace the namespace
    * @param version the version
    * @param workspace the workspace containing the namespace version, {@code null} to determine it
    * @return the files that use its elements
    * @throws IllegalArgumentException if a value is invalid or several workspaces contain the namespace version
    * @throws ReadOnlySourceException if only a read-only source contains the namespace version
    */
   public ReferenceReport checkNamespace( final String namespace, final String version, final @Nullable String workspace ) {
      final Path root = resolveNamespaceRoot( namespace, version, workspace );
      return referenceService.findNamespaceReferences( root, namespace, version );
   }

   /**
    * Checks whether an Aspect Model file can be deleted.
    *
    * @param namespace the namespace of the file
    * @param version the version of the file
    * @param fileName the file name
    * @return the other files that use its elements
    * @throws IllegalArgumentException if a name is invalid
    * @throws FileNotFoundException if the file does not exist
    */
   public ReferenceReport checkFile( final String namespace, final String version, final String fileName ) {
      return checkFile( namespace, version, fileName, null );
   }

   /**
    * Checks whether an Aspect Model file of a workspace can be deleted.
    *
    * @param namespace the namespace of the file
    * @param version the version of the file
    * @param fileName the file name
    * @param workspace the workspace containing the file, {@code null} to determine it
    * @return the other files that use its elements
    * @throws IllegalArgumentException if a value is invalid or several workspaces contain the file
    * @throws FileNotFoundException if the file does not exist
    * @throws ReadOnlySourceException if only a read-only source contains the file
    */
   public ReferenceReport checkFile( final String namespace, final String version, final String fileName,
         final @Nullable String workspace ) {
      final Path root = resolveRoot( workspace, candidate -> Files.isRegularFile(
                  filePathResolver.resolveModelFile( candidate, namespace, version, fileName ) ),
            "Aspect Model file not found: " + namespace + ":" + version + "/" + fileName );
      return referenceService.findFileReferences( filePathResolver.resolveModelFile( root, namespace, version, fileName ) );
   }

   /**
    * Deletes all Aspect Model files of a namespace version if no file of another namespace or version uses them.
    *
    * @param namespace the namespace
    * @param version the version
    * @return the number of deleted files
    * @throws ModelReferencedException if the namespace is still used
    * @throws FileNotFoundException if the namespace version does not exist
    */
   public int deleteNamespace( final String namespace, final String version ) {
      return deleteNamespace( namespace, version, null );
   }

   /**
    * Deletes all Aspect Model files of a namespace version in a workspace if no other file uses them.
    *
    * @param namespace the namespace
    * @param version the version
    * @param workspace the workspace containing the namespace version, {@code null} to determine it
    * @return the number of deleted files
    * @throws ModelReferencedException if the namespace is still used
    * @throws FileNotFoundException if the namespace version does not exist
    * @throws ReadOnlySourceException if only a read-only source contains the namespace version
    * @throws IllegalArgumentException if several workspaces contain the namespace version
    */
   public int deleteNamespace( final String namespace, final String version, final @Nullable String workspace ) {
      final Path root = resolveNamespaceRoot( namespace, version, workspace );
      final Path versionDirectory = filePathResolver.resolveVersionDirectory( root, namespace, version );
      final ReferenceReport report = referenceService.findNamespaceReferences( root, namespace, version );
      if ( !report.deletable() ) {
         throw new ModelReferencedException( "Namespace '" + namespace + ":" + version + "' is still used by other files", report );
      }

      final List<WorkspaceFile> files = referenceService.listWorkspaceFiles( root ).stream()
            .filter( file -> file.isIn( namespace, version ) )
            .toList();
      deleteFiles( files );
      fileOperations.deleteDirectoryIfEmpty( versionDirectory );
      fileOperations.deleteDirectoryIfEmpty( versionDirectory.getParent() );
      LOG.info( "Deleted namespace {}:{} with {} file(s) in {}", namespace, version, files.size(), root );
      return files.size();
   }

   /**
    * Deletes all Aspect Model files of the active workspace.
    *
    * @param backup whether a backup of the workspace should be created first
    * @return the number of deleted files and whether a backup was created
    */
   public ClearWorkspaceResult clearWorkspace( final boolean backup ) {
      return clearWorkspace( backup, null );
   }

   /**
    * Deletes all Aspect Model files of a workspace. No reference check is needed because everything is deleted.
    *
    * @param backup whether a backup of the workspace should be created first
    * @param workspace the workspace to clear, {@code null} for the active workspace
    * @return the number of deleted files and whether a backup was created
    */
   public ClearWorkspaceResult clearWorkspace( final boolean backup, final @Nullable String workspace ) {
      final Path root = referenceService.resolveWorkspace( workspace );
      final List<WorkspaceFile> files = referenceService.listWorkspaceFiles( root );
      final boolean backupCreated = backup && !files.isEmpty();
      if ( backupCreated ) {
         packageService.backupDirectory( root );
      }
      deleteFiles( files );
      // Also removes namespace/version folders left empty by earlier deletions; folders with other content are kept.
      fileOperations.listSubdirectories( root ).forEach( namespaceDirectory -> {
         fileOperations.listSubdirectories( namespaceDirectory ).forEach( fileOperations::deleteDirectoryIfEmpty );
         fileOperations.deleteDirectoryIfEmpty( namespaceDirectory );
      } );
      LOG.info( "Cleared workspace {}, deleted {} file(s), backup created: {}", root, files.size(), backupCreated );
      return new ClearWorkspaceResult( files.size(), backupCreated );
   }

   private Path resolveNamespaceRoot( final String namespace, final String version, final @Nullable String workspace ) {
      // validates namespace and version before any lookup
      filePathResolver.resolveVersionDirectory( referenceService.getDefaultModelPath(), namespace, version );
      return resolveRoot( workspace,
            candidate -> Files.isDirectory( filePathResolver.resolveVersionDirectory( candidate, namespace, version ) ),
            "Namespace not found: " + namespace + ":" + version );
   }

   /**
    * Determines the writable workspace to change: the given one, or else the only workspace containing the target.
    */
   private Path resolveRoot( final @Nullable String workspace, final Predicate<Path> containsTarget, final String notFoundMessage ) {
      if ( workspace != null && !workspace.isBlank() ) {
         final Path root = referenceService.resolveWorkspace( workspace );
         if ( !containsTarget.test( root ) ) {
            throw new FileNotFoundException( notFoundMessage );
         }
         return root;
      }
      final List<Path> writable = referenceService.getWritableRoots().stream().filter( containsTarget ).toList();
      if ( writable.size() == 1 ) {
         return writable.getFirst();
      }
      if ( writable.size() > 1 ) {
         throw new IllegalArgumentException( notFoundMessage.replace( "not found", "exists in several workspaces" )
               + ". Please specify the workspace." );
      }
      if ( referenceService.getReadableRoots().stream().anyMatch( containsTarget ) ) {
         throw new ReadOnlySourceException( "Models of GitHub sources are read-only." );
      }
      throw new FileNotFoundException( notFoundMessage );
   }

   private void deleteFiles( final List<WorkspaceFile> files ) {
      files.forEach( file -> fileOperations.deleteFileSafely( file.path() ) );
   }
}
