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

package org.eclipse.esmf.ame.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.esmf.ame.constants.ApplicationConstants;
import org.eclipse.esmf.ame.exceptions.CreateFileException;
import org.eclipse.esmf.ame.exceptions.FileReadException;
import org.eclipse.esmf.ame.exceptions.InvalidAspectModelException;
import org.eclipse.esmf.ame.services.models.AasImportCandidate;
import org.eclipse.esmf.ame.services.models.Version;
import org.eclipse.esmf.ame.services.utils.ModelGroupingUtils;
import org.eclipse.esmf.ame.services.workspace.WorkspaceConfigService;
import org.eclipse.esmf.aspectmodel.AspectModelFile;
import org.eclipse.esmf.aspectmodel.ValueParsingException;
import org.eclipse.esmf.aspectmodel.aas.AasFileFormat;
import org.eclipse.esmf.aspectmodel.aas.AasToAspectModelGenerator;
import org.eclipse.esmf.aspectmodel.edit.AspectChangeManager;
import org.eclipse.esmf.aspectmodel.edit.AspectChangeManagerConfig;
import org.eclipse.esmf.aspectmodel.edit.AspectChangeManagerConfigBuilder;
import org.eclipse.esmf.aspectmodel.edit.Change;
import org.eclipse.esmf.aspectmodel.edit.ChangeGroup;
import org.eclipse.esmf.aspectmodel.edit.change.AddAspectModelFile;
import org.eclipse.esmf.aspectmodel.generator.AspectArtifact;
import org.eclipse.esmf.aspectmodel.loader.AspectModelLoader;
import org.eclipse.esmf.aspectmodel.resolver.NamespacePackage;
import org.eclipse.esmf.aspectmodel.resolver.fs.ModelsRoot;
import org.eclipse.esmf.aspectmodel.resolver.fs.StructuredModelsRoot;
import org.eclipse.esmf.aspectmodel.resolver.modelfile.RawAspectModelFileBuilder;
import org.eclipse.esmf.aspectmodel.serializer.AspectSerializer;
import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;
import org.eclipse.esmf.aspectmodel.validation.services.AspectModelValidator;
import org.eclipse.esmf.metamodel.Aspect;
import org.eclipse.esmf.metamodel.AspectModel;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.multipart.CompletedFileUpload;
import jakarta.inject.Singleton;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class for handling package import and export operations for Aspect Models.
 */
@Singleton
public class PackageService {
   private static final Logger LOG = LoggerFactory.getLogger( PackageService.class );
   private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern( "yyyy.MM.dd-HH.mm.ss" );

   private final AspectModelLoader aspectModelLoader;
   private final AspectModelValidator aspectModelValidator;
   private final Path modelPath;
   private final @Nullable WorkspaceConfigService workspaceConfigService;

   public PackageService( final AspectModelLoader aspectModelLoader, final Path modelPath,
         final AspectModelValidator aspectModelValidator,
         final @Nullable WorkspaceConfigService workspaceConfigService ) {
      this.aspectModelLoader = aspectModelLoader;
      this.modelPath = modelPath;
      this.aspectModelValidator = aspectModelValidator;
      this.workspaceConfigService = workspaceConfigService;
   }

   public PackageService( final AspectModelLoader aspectModelLoader, final Path modelPath,
         final AspectModelValidator aspectModelValidator ) {
      this( aspectModelLoader, modelPath, aspectModelValidator, null );
   }

   private Path resolveWorkspace( final @Nullable String workspace ) {
      if ( workspaceConfigService != null ) {
         return workspaceConfigService.resolveTargetWorkspace( workspace );
      }
      return modelPath;
   }

   public byte[] exportPackage( final String aspectModelUrn ) {
      final AspectModel aspectModel = aspectModelLoader.load( AspectModelUrn.fromUrn( aspectModelUrn ) );
      return new NamespacePackage( aspectModel ).serialize();
   }

   public Map<String, List<Version>> importPackage( final CompletedFileUpload zipFile ) {
      return importPackage( zipFile, null );
   }

   /**
    * Imports the Aspect Model files of a package into a workspace.
    *
    * @param zipFile the package
    * @param workspace the target workspace, {@code null} for the active workspace
    * @return the imported models grouped by namespace and version
    */
   public Map<String, List<Version>> importPackage( final CompletedFileUpload zipFile, final @Nullable String workspace ) {
      try {
         final ModelsRoot modelsRoot = new StructuredModelsRoot( resolveWorkspace( workspace ) );
         final byte[] zipContent = IOUtils.toByteArray( zipFile.getInputStream() );
         final NamespacePackage namespacePackage = new NamespacePackage( zipContent, null );

         final List<Change> fileChanges = namespacePackage.loadContents().<Change> map( file -> createAddChange( file, modelsRoot ) )
               .toList();

         final AspectChangeManager changeManager = initChangeManager();
         changeManager.applyChange( new ChangeGroup( fileChanges ) );

         final List<File> list = saveAspectModelFiles( changeManager.aspectModelFiles() ).map( File::new ).toList();

         return groupByNamespaceAndVersion( list );
      } catch ( final ValueParsingException | IOException e ) {
         if ( e instanceof ValueParsingException ) {
            throw new InvalidAspectModelException( "The structure inside the " + ApplicationConstants.FileExtensions.ZIP + " file does not match the expected format.", e );
         } else {
            throw new FileReadException( "Could not read from input: " + e.getMessage(), e );
         }
      }
   }

   /**
    * Lists the Aspect Models that would be created from the submodel templates of an Asset Administration Shell file.
    *
    * @param aasFile the AAS file (AASX, AAS XML or AAS JSON)
    * @param workspace the target workspace, {@code null} for the default workspace
    * @return one candidate per submodel template
    */
   public List<AasImportCandidate> previewAasImport( final CompletedFileUpload aasFile, final @Nullable String workspace ) {
      final ModelsRoot modelsRoot = new StructuredModelsRoot( resolveWorkspace( workspace ) );

      return convertAas( aasFile ).stream().map( aspect -> {
         final AspectModelUrn urn = aspect.urn();
         final Path target = aasImportTarget( aspect, modelsRoot );
         return new AasImportCandidate( urn.toString(), urn.getNamespaceMainPart(), urn.getVersion(), target.getFileName().toString(),
               Files.exists( target ) );
      } ).toList();
   }

   /**
    * Converts the submodel templates of an Asset Administration Shell file into Aspect Models and stores them in a workspace.
    * Existing files are never overwritten.
    *
    * @param aasFile the AAS file (AASX, AAS XML or AAS JSON)
    * @param workspace the target workspace, {@code null} for the default workspace
    * @return the imported models grouped by namespace and version
    * @throws CreateFileException if a target file already exists or cannot be written
    */
   public Map<String, List<Version>> importAas( final CompletedFileUpload aasFile, final @Nullable String workspace ) {
      final ModelsRoot modelsRoot = new StructuredModelsRoot( resolveWorkspace( workspace ) );
      final List<Aspect> aspects = convertAas( aasFile );

      final List<Path> existingTargets = aspects.stream().map( aspect -> aasImportTarget( aspect, modelsRoot ) ).filter( Files::exists )
            .toList();
      if ( !existingTargets.isEmpty() ) {
         throw new CreateFileException( "The following Aspect Model files already exist: " + existingTargets.stream()
               .map( path -> path.getFileName().toString() ).toList() );
      }

      final List<File> written = aspects.stream().map( aspect -> writeAspect( aspect, aasImportTarget( aspect, modelsRoot ) ) ).toList();
      return groupByNamespaceAndVersion( written );
   }

   private List<Aspect> convertAas( final CompletedFileUpload aasFile ) {
      final AasFileFormat format = aasFileFormat( aasFile.getFilename() );
      final List<Aspect> aspects;
      try ( final InputStream inputStream = aasFile.getInputStream() ) {
         aspects = aasGenerator( format, inputStream ).generate().map( AspectArtifact::getContent ).toList();
      } catch ( final IOException e ) {
         throw new FileReadException( "Could not read from input: " + e.getMessage(), e );
      } catch ( final RuntimeException e ) {
         throw new InvalidAspectModelException( String.format( "The file '%s' is not a valid Asset Administration Shell: %s",
               aasFile.getFilename(), e.getMessage() ), e );
      }

      if ( aspects.isEmpty() ) {
         throw new InvalidAspectModelException(
               String.format( "The file '%s' does not contain any submodel templates.", aasFile.getFilename() ) );
      }
      return aspects;
   }

   private AasFileFormat aasFileFormat( final String fileName ) {
      return switch ( FilenameUtils.getExtension( fileName ).toLowerCase( Locale.ROOT ) ) {
         case "aasx" -> AasFileFormat.AASX;
         case "xml" -> AasFileFormat.XML;
         case "json" -> AasFileFormat.JSON;
         default -> throw new FileReadException(
               String.format( "The selected file '%s' is not an AASX, AAS XML or AAS JSON file.", fileName ) );
      };
   }

   private AasToAspectModelGenerator aasGenerator( final AasFileFormat format, final InputStream inputStream ) {
      return switch ( format ) {
         case AASX -> AasToAspectModelGenerator.fromAasx( inputStream );
         case XML -> AasToAspectModelGenerator.fromAasXml( inputStream );
         case JSON -> AasToAspectModelGenerator.fromAasJson( inputStream );
      };
   }

   private Path aasImportTarget( final Aspect aspect, final ModelsRoot modelsRoot ) {
      return modelsRoot.directoryForNamespace( aspect.urn() ).resolve( aspect.getName() + ApplicationConstants.FileExtensions.TTL );
   }

   private File writeAspect( final Aspect aspect, final Path target ) {
      try {
         Files.createDirectories( target.getParent() );
         Files.writeString( target, AspectSerializer.INSTANCE.aspectToString( aspect ) );
         return target.toFile();
      } catch ( final IOException e ) {
         throw new CreateFileException( "Could not write Aspect Model file: " + target.getFileName(), e );
      }
   }

   private Map<String, List<Version>> groupByNamespaceAndVersion( final List<File> files ) {
      return new ModelGroupingUtils( aspectModelLoader, aspectModelValidator,
            workspaceConfigService != null ? workspaceConfigService::findSource : null ).groupModelsByNamespaceAndVersion( files );
   }

   private AddAspectModelFile createAddChange( final AspectModelFile file, final ModelsRoot modelsRoot ) {
      if ( file.sourceModel().isEmpty() ) {
         // Source model should always be present after loading from package
         // This indicates a malformed file in the package
         final String fileName = file.filename().orElse( "unknown" );
         LOG.warn( "Source model is empty for file: {} with namespace: {}", fileName, file.namespaceUrn() );
         throw new InvalidAspectModelException( String.format(
               "Source model is empty for file '%s'. The package may be malformed or corrupted.", fileName ) );
      }

      final URI targetLocation = modelsRoot.directoryForNamespace( file.namespaceUrn() ).resolve( file.filename().orElseThrow() ).toUri();

      final RawAspectModelFileBuilder builder = RawAspectModelFileBuilder.builder().sourceModel( file.sourceModel() )
            .sourceUri( targetLocation ).headerComment( file.headerComment() );

      return new AddAspectModelFile( builder.build() );
   }

   private AspectChangeManager initChangeManager() {
      final AspectChangeManagerConfig config = AspectChangeManagerConfigBuilder.builder().build();
      return new AspectChangeManager( config, new AspectModelLoader().emptyModel() );
   }

   private Stream<URI> saveAspectModelFiles( final Stream<AspectModelFile> files ) {
      return files.peek( this::ensureParentDirectoryExists ).peek( AspectSerializer.INSTANCE::write ).map( AspectModelFile::sourceUri );
   }

   private void ensureParentDirectoryExists( final AspectModelFile file ) {
      final File parent = Paths.get( file.sourceUri() ).toFile().getParentFile();
      if ( parent != null && !parent.exists() ) {
         parent.mkdirs();
      }
   }

   public void backupWorkspace() {
      backupWorkspace( null );
   }

   /**
    * Creates a backup zip of all Turtle files of a workspace inside the workspace.
    *
    * @param workspace the workspace, {@code null} for the active workspace
    */
   public void backupWorkspace( final @Nullable String workspace ) {
      backupDirectory( resolveWorkspace( workspace ) );
   }

   /**
    * Creates a backup zip of all Turtle files of a workspace directory inside the directory.
    *
    * @param targetModelPath the workspace directory
    */
   public void backupDirectory( final Path targetModelPath ) {
      try {
         final String timestamp = LocalDateTime.now().format( BACKUP_TIMESTAMP_FORMAT );
         final String zipFileName = targetModelPath.resolve( "backup-" + timestamp + ApplicationConstants.FileExtensions.ZIP ).toString();

         try ( final ZipOutputStream zos = new ZipOutputStream( new FileOutputStream( zipFileName ) );
               final Stream<Path> paths = Files.walk( targetModelPath ) ) {

            paths.filter( Files::isRegularFile ).filter( path -> path.toString().endsWith( ApplicationConstants.FileExtensions.TTL ) ).forEach( filePath -> {
               try {
                  final ZipEntry zipEntry = new ZipEntry( targetModelPath.relativize( filePath ).toString() );
                  zos.putNextEntry( zipEntry );
                  Files.copy( filePath, zos );
                  zos.closeEntry();
               } catch ( final IOException e ) {
                  LOG.error( "Error while zipping file: {}", filePath, e );
                  throw new CreateFileException( "Error while zipping file: " + filePath, e );
               }
            } );
         }
      } catch ( final IOException e ) {
         LOG.error( "Failed to create the zip file." );
         throw new CreateFileException( "An error occurred while creating the zip file.", e );
      }
   }
}
