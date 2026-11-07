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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.esmf.ame.exceptions.CreateFileException;
import org.eclipse.esmf.ame.exceptions.FileReadException;
import org.eclipse.esmf.ame.exceptions.InvalidAspectModelException;
import org.eclipse.esmf.ame.model.MockFileUpload;
import org.eclipse.esmf.ame.services.models.AasImportCandidate;
import org.eclipse.esmf.ame.services.models.Version;
import org.eclipse.esmf.aspectmodel.aas.AasFileFormat;
import org.eclipse.esmf.aspectmodel.aas.AasGenerationConfigBuilder;
import org.eclipse.esmf.aspectmodel.aas.AspectModelAasGenerator;
import org.eclipse.esmf.aspectmodel.loader.AspectModelLoader;

import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@MicronautTest
class PackageServiceTest {
   @Inject
   private PackageService packageService;

   @TempDir
   private Path tempDir;

   private static final String FILE_EXTENSION = ".ttl";

   private static final Path RESOURCE_PATH = Path.of( "src", "test", "resources", "services" );

   private static final String SAMM_URN = "urn:samm";
   private static final String VERSION = "1.0.0";
   private static final String EXPORT_NAMESPACE = "org.eclipse.esmf.export";

   private static final String NAMESPACE_VERSION = SAMM_URN + ":" + EXPORT_NAMESPACE + ":" + VERSION;

   private static final String FILE_ONE = "TestFileOne";

   private static final String AAS_NAMESPACE = "org.eclipse.esmf.aas";

   private static final String AAS_ASPECT_MODEL = """
         @prefix samm: <urn:samm:org.eclipse.esmf.samm:meta-model:2.2.0#> .
         @prefix samm-c: <urn:samm:org.eclipse.esmf.samm:characteristic:2.2.0#> .
         @prefix : <urn:samm:org.eclipse.esmf.aas:1.0.0#> .

         :ImportedAspect a samm:Aspect ;
            samm:preferredName "Imported Aspect"@en ;
            samm:properties ( :name ) ;
            samm:operations ( ) .

         :name a samm:Property ;
            samm:preferredName "Name"@en ;
            samm:characteristic samm-c:Text .
         """;

   @AfterEach
   void tearDown() throws IOException {
      final Path importedFile = RESOURCE_PATH.resolve( Path.of( "org.eclipse.esmf.example", "1.0.0", "Esmf.ttl" ) );
      Files.deleteIfExists( importedFile );

      deleteDirectory( RESOURCE_PATH.resolve( "org.eclipse.esmf.test" ) );
      deleteDirectory( RESOURCE_PATH.resolve( AAS_NAMESPACE ) );

      try ( final Stream<Path> stream = Files.list( RESOURCE_PATH ) ) {
         stream.filter( path -> path.getFileName().toString().startsWith( "backup-" )
               && path.getFileName().toString().endsWith( ".zip" ) )
               .forEach( path -> {
                  try {
                     Files.deleteIfExists( path );
                  } catch ( final IOException ignored ) {
                  }
               } );
      }
   }

   private static void deleteDirectory( final Path directory ) throws IOException {
      if ( Files.exists( directory ) ) {
         try ( final Stream<Path> stream = Files.walk( directory ) ) {
            stream.sorted( Comparator.reverseOrder() ).forEach( path -> {
               try {
                  Files.deleteIfExists( path );
               } catch ( final IOException ignored ) {
               }
            } );
         }
      }
   }

   @Test
   void testExportAspectModelPackage() {
      final byte[] exportPackage = packageService.exportPackage( NAMESPACE_VERSION + "#" + FILE_ONE );

      assertDoesNotThrow( () -> {
         try ( final ByteArrayInputStream bais = new ByteArrayInputStream( exportPackage );
               final ZipInputStream zis = new ZipInputStream( bais ) ) {
            ZipEntry entry;
            while ( ( entry = zis.getNextEntry() ) != null ) {
               assertNotNull( entry.getName() );
               assertFalse( entry.getName().isEmpty() );
               assertTrue( entry.getName().contains( "org.eclipse.esmf.export" ) || entry.getName().contains( "1.0.0" ) || entry.getName()
                     .contains( "TestFileOne" + FILE_EXTENSION ) || entry.getName().contains( "TestFileThree" + FILE_EXTENSION ) );
               assertFalse( entry.getName().contains( "TestFileTwo" + FILE_EXTENSION ) );
            }
         }
      } );
   }

   @Test
   void testImportAspectModelPackage() throws IOException {
      final Path zipFilePath = Paths.get( RESOURCE_PATH.toString(), "TestArchive.zip" );
      final byte[] testPackage = Files.readAllBytes( zipFilePath );

      final CompletedFileUpload mockedZipFile = MockFileUpload.create( "TestArchive.zip", testPackage, MediaType.APPLICATION_PDF_TYPE );

      packageService.importPackage( mockedZipFile );

      try ( final ZipInputStream zis = new ZipInputStream( new ByteArrayInputStream( testPackage ) ) ) {
         ZipEntry entry;
         while ( ( entry = zis.getNextEntry() ) != null ) {
            if ( !entry.isDirectory() && entry.getName().endsWith( FILE_EXTENSION ) ) {
               final Path extractedFilePath = RESOURCE_PATH.resolve( entry.getName() );
               System.out.println( extractedFilePath );
               assertTrue( Files.exists( extractedFilePath ), "File " + entry.getName() + " should exist" );
            }
         }
      }
   }

   @Test
   void testBackupWorkspace() {
      packageService.backupWorkspace();

      assertTrue( Arrays.stream( Objects.requireNonNull( RESOURCE_PATH.toFile().list() ) ).anyMatch( file -> file.contains( "backup-" ) ) );
   }

   @Test
   void testPreviewAasImportListsSubmodelTemplates() throws IOException {
      final CompletedFileUpload aasx = aasxUpload( aasAspectModelFile() );

      final List<AasImportCandidate> candidates = packageService.previewAasImport( aasx, null );

      assertEquals( List.of(
            new AasImportCandidate( "urn:samm:org.eclipse.esmf.aas:1.0.0#ImportedAspect", AAS_NAMESPACE, VERSION, "ImportedAspect.ttl",
                  false ) ), candidates );
   }

   @Test
   void testPreviewAasImportMarksExistingModels() throws IOException {
      final CompletedFileUpload aasx = aasxUpload( RESOURCE_PATH.resolve( Path.of( "org.eclipse.esmf.example", VERSION, "Movement.ttl" ) ) );

      final List<AasImportCandidate> candidates = packageService.previewAasImport( aasx, null );

      assertEquals( 1, candidates.size() );
      assertEquals( "Movement.ttl", candidates.getFirst().fileName() );
      assertTrue( candidates.getFirst().alreadyExists() );
   }

   @Test
   void testImportAasWritesAspectModelIntoWorkspace() throws IOException {
      final CompletedFileUpload aasx = aasxUpload( aasAspectModelFile() );

      final Map<String, List<Version>> imported = packageService.importAas( aasx, null );

      final Path importedFile = RESOURCE_PATH.resolve( Path.of( AAS_NAMESPACE, VERSION, "ImportedAspect.ttl" ) );
      assertTrue( Files.exists( importedFile ) );
      assertTrue( Files.readString( importedFile ).contains( ":ImportedAspect a samm:Aspect" ) );
      assertTrue( imported.containsKey( AAS_NAMESPACE ) );
      assertDoesNotThrow( () -> new AspectModelLoader().load( importedFile.toFile() ) );
   }

   @Test
   void testImportAasDoesNotOverwriteExistingModels() throws IOException {
      final Path movement = RESOURCE_PATH.resolve( Path.of( "org.eclipse.esmf.example", VERSION, "Movement.ttl" ) );
      final String original = Files.readString( movement );
      final CompletedFileUpload aasx = aasxUpload( movement );

      final CreateFileException exception = assertThrows( CreateFileException.class, () -> packageService.importAas( aasx, null ) );

      assertTrue( exception.getMessage().contains( "Movement.ttl" ) );
      assertEquals( original, Files.readString( movement ) );
   }

   @Test
   void testImportAasRejectsUnsupportedFileType() {
      final CompletedFileUpload upload = MockFileUpload.create( "model.ttl", new byte[0], MediaType.MULTIPART_FORM_DATA_TYPE );

      assertThrows( FileReadException.class, () -> packageService.previewAasImport( upload, null ) );
   }

   @Test
   void testImportAasRejectsInvalidContent() {
      final CompletedFileUpload upload = MockFileUpload.create( "broken.json", "{ not json".getBytes(),
            MediaType.MULTIPART_FORM_DATA_TYPE );

      assertThrows( InvalidAspectModelException.class, () -> packageService.importAas( upload, null ) );
   }

   private Path aasAspectModelFile() throws IOException {
      final Path directory = tempDir.resolve( Path.of( AAS_NAMESPACE, VERSION ) );
      Files.createDirectories( directory );
      final Path file = directory.resolve( "ImportedAspect.ttl" );
      Files.writeString( file, AAS_ASPECT_MODEL );
      return file;
   }

   private static CompletedFileUpload aasxUpload( final Path aspectModelFile ) {
      final AspectModelAasGenerator generator = new AspectModelAasGenerator(
            new AspectModelLoader().load( aspectModelFile.toFile() ).aspect(),
            AasGenerationConfigBuilder.builder().format( AasFileFormat.AASX ).build() );
      return MockFileUpload.create( "model.aasx", generator.getContent(), MediaType.MULTIPART_FORM_DATA_TYPE );
   }
}
