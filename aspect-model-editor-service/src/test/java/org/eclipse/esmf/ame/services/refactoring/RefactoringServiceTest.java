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

import static org.eclipse.esmf.ame.services.workspace.WorkspaceTestFiles.consumerB;
import static org.eclipse.esmf.ame.services.workspace.WorkspaceTestFiles.providerA;
import static org.eclipse.esmf.ame.services.workspace.WorkspaceTestFiles.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.eclipse.esmf.ame.exceptions.ReadOnlySourceException;
import org.eclipse.esmf.ame.exceptions.RefactoringException;
import org.eclipse.esmf.ame.services.file.FilePathResolver;
import org.eclipse.esmf.ame.services.models.refactoring.ChangedFile;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringHistoryEntry;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringPhase;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringPreview;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringProgress;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringRequest;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringType;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceConfig;
import org.eclipse.esmf.ame.services.workspace.DynamicResolutionStrategy;
import org.eclipse.esmf.ame.services.workspace.WorkspaceConfigService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;
import org.eclipse.esmf.aspectmodel.edit.change.IncreaseVersion;
import org.eclipse.esmf.aspectmodel.loader.AspectModelLoader;

import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Refactorings across several workspaces and read-only GitHub sources, including their revert.
 */
class RefactoringServiceTest {
   private static final String SHARED_PROPERTY = "urn:samm:org.example.a:1.0.0#sharedProperty";
   private static final String SHARED_CHARACTERISTIC = "urn:samm:org.example.a:1.0.0#SharedCharacteristic";

   @TempDir
   Path tempDir;

   private Path defaultWorkspace;
   private Path second;
   private WorkspaceConfigService configService;
   private DynamicResolutionStrategy resolutionStrategy;
   private RefactoringService service;

   @BeforeEach
   void setUp() {
      defaultWorkspace = tempDir.resolve( "models" ).toAbsolutePath().normalize();
      second = tempDir.resolve( "second" ).toAbsolutePath().normalize();
      configService = new WorkspaceConfigService( tempDir.resolve( "workspaces.json" ), defaultWorkspace, JsonMapper.createDefault() );
      configService.addWorkspace( second.toString() );
      resolutionStrategy = new DynamicResolutionStrategy( configService );
      service = refactoringService( new RefactoringHistoryService( tempDir.resolve( "history" ), JsonMapper.createDefault() ) );
   }

   private RefactoringService refactoringService( final RefactoringHistoryService historyService ) {
      return new RefactoringService( new AspectModelLoader( resolutionStrategy ),
            new WorkspaceReferenceService( defaultWorkspace, configService ), new FilePathResolver(), historyService, resolutionStrategy );
   }

   @Test
   void previewOfARenameListsTheFilesOfAllWorkspacesWithoutChangingThem() throws IOException {
      final Path provider = providerA( defaultWorkspace );
      final Path consumer = consumerB( second );
      final String original = Files.readString( consumer );

      final RefactoringPreview preview = service.preview( renameElement( SHARED_PROPERTY, "renamedProperty" ) );

      assertEquals( List.of( provider.toString(), consumer.toString() ).stream().sorted().toList(), paths( preview.modified() ) );
      assertTrue( preview.created().isEmpty() );
      assertTrue( preview.removed().isEmpty() );
      assertTrue( preview.blocked().isEmpty() );
      assertTrue( preview.applicable() );
      assertEquals( "Rename sharedProperty to renamedProperty", preview.description() );
      assertEquals( original, Files.readString( consumer ) );
      assertTrue( service.getHistory().isEmpty() );
   }

   @Test
   void renameUpdatesTheReferencesInOtherWorkspacesAndCanBeReverted() throws IOException {
      final Path provider = providerA( defaultWorkspace );
      final Path consumer = consumerB( second );
      final String originalProvider = Files.readString( provider );
      final String originalConsumer = Files.readString( consumer );

      final RefactoringHistoryEntry entry = service.apply( renameElement( SHARED_PROPERTY, "renamedProperty" ) );

      assertTrue( Files.readString( provider ).contains( ":renamedProperty" ) );
      assertTrue( Files.readString( consumer ).contains( "a:renamedProperty" ) );
      assertFalse( Files.readString( consumer ).contains( "sharedProperty" ) );
      assertEquals( List.of( entry ), service.getHistory() );

      service.revert( entry.id(), false );

      assertEquals( originalProvider, Files.readString( provider ) );
      assertEquals( originalConsumer, Files.readString( consumer ) );
      assertTrue( service.getHistory().isEmpty() );
   }

   @Test
   void applyReportsItsProgressWhileWritingAndIsIdleAfterwards() throws IOException {
      providerA( defaultWorkspace );
      consumerB( second );
      final List<RefactoringProgress> observed = new ArrayList<>();
      service = refactoringService( new RefactoringHistoryService( tempDir.resolve( "history" ), JsonMapper.createDefault() ) {
         @Override
         public synchronized RefactoringHistoryEntry record( final String description, final List<ChangedFile> created,
               final List<ChangedFile> modified, final List<ChangedFile> removed, final Runnable write ) {
            observed.add( service.getProgress() );
            return super.record( description, created, modified, removed, write );
         }
      } );
      assertEquals( RefactoringProgress.IDLE, service.getProgress() );

      service.apply( renameElement( SHARED_PROPERTY, "renamedProperty" ) );

      assertEquals( List.of( new RefactoringProgress( true, RefactoringPhase.WRITING, 3, 3, 2 ) ), observed );
      assertEquals( RefactoringProgress.IDLE, service.getProgress() );
   }

   @Test
   void progressIsIdleAfterAFailedPreview() {
      providerA( defaultWorkspace );

      assertThrows( IllegalArgumentException.class, () -> service.preview( renameElement( SHARED_PROPERTY, null ) ) );

      assertEquals( RefactoringProgress.IDLE, service.getProgress() );
   }

   @Test
   void revertIsRefusedForFilesChangedAfterTheRefactoringUnlessForced() throws IOException {
      final Path provider = providerA( defaultWorkspace );
      consumerB( second );
      final String originalProvider = Files.readString( provider );
      final RefactoringHistoryEntry entry = service.apply( renameElement( SHARED_PROPERTY, "renamedProperty" ) );
      Files.writeString( provider, Files.readString( provider ) + "\n# edited\n" );

      final RefactoringException conflict = assertThrows( RefactoringException.class, () -> service.revert( entry.id(), false ) );
      assertTrue( conflict.getMessage().contains( provider.toString() ) );

      service.revert( entry.id(), true );
      assertEquals( originalProvider, Files.readString( provider ) );
   }

   @Test
   void renameNamespaceMovesTheFilesAndUpdatesReferencesAndPrefixes() throws IOException {
      final Path provider = providerA( defaultWorkspace );
      final Path consumer = consumerB( second );

      final RefactoringHistoryEntry entry = service.apply( new RefactoringRequest( RefactoringType.RENAME_NAMESPACE, null, null,
            "org.example.a", "1.0.0", null, null, "org.example.c", "2.0.0", null, null, null ) );

      final Path moved = defaultWorkspace.resolve( "org.example.c" ).resolve( "2.0.0" ).resolve( "Provider.ttl" );
      assertEquals( List.of( moved.toString() ), paths( entry.created() ) );
      assertEquals( List.of( provider.toString() ), paths( entry.removed() ) );
      assertFalse( Files.exists( defaultWorkspace.resolve( "org.example.a" ) ) );
      assertTrue( Files.readString( moved ).contains( "<urn:samm:org.example.c:2.0.0#>" ) );
      assertTrue( Files.readString( consumer ).contains( "<urn:samm:org.example.c:2.0.0#>" ) );
      assertFalse( Files.readString( consumer ).contains( "org.example.a" ) );

      service.revert( entry.id(), false );
      assertTrue( Files.exists( provider ) );
      assertFalse( Files.exists( defaultWorkspace.resolve( "org.example.c" ) ) );
   }

   @Test
   void moveElementToANewFileOfAnotherNamespaceUpdatesTheReferences() throws IOException {
      final Path provider = providerA( defaultWorkspace );

      final RefactoringHistoryEntry entry = service.apply( moveElement( SHARED_CHARACTERISTIC, "org.example.b", "1.0.0",
            "Characteristics.ttl" ) );

      final Path target = defaultWorkspace.resolve( "org.example.b" ).resolve( "1.0.0" ).resolve( "Characteristics.ttl" );
      assertEquals( List.of( target.toString() ), paths( entry.created() ) );
      assertTrue( Files.readString( target ).contains( "<urn:samm:org.example.b:1.0.0#>" ) );
      assertTrue( Files.readString( target ).contains( "SharedCharacteristic" ) );
      assertTrue( Files.readString( provider ).contains( "urn:samm:org.example.b:1.0.0#SharedCharacteristic" ) );
   }

   @Test
   void moveElementToAnExistingFileOfTheSameNamespace() throws IOException {
      final Path provider = providerA( defaultWorkspace );
      final Path other = write( defaultWorkspace, "org.example.a", "1.0.0", "Other.ttl", """
            @prefix : <urn:samm:org.example.a:1.0.0#> .
            :otherProperty a samm:Property ; samm:characteristic samm-c:Text .
            """ );

      final RefactoringHistoryEntry entry = service.apply( moveElement( SHARED_CHARACTERISTIC, null, null, "Other.ttl" ) );

      assertEquals( List.of( other.toString(), provider.toString() ).stream().sorted().toList(), paths( entry.modified() ) );
      assertTrue( Files.readString( other ).contains( "SharedCharacteristic" ) );
      assertFalse( Files.readString( provider ).contains( "SharedCharacteristic a samm:Characteristic" ) );
   }

   @Test
   void newVersionCopiesTheNamespace() throws IOException {
      final Path provider = providerA( defaultWorkspace );
      final String original = Files.readString( provider );

      final RefactoringHistoryEntry entry = service.apply( new RefactoringRequest( RefactoringType.NEW_VERSION, null, null,
            "org.example.a", "1.0.0", null, null, null, null, null, null, IncreaseVersion.MINOR ) );

      final Path copy = defaultWorkspace.resolve( "org.example.a" ).resolve( "1.1.0" ).resolve( "Provider.ttl" );
      assertEquals( List.of( copy.toString() ), paths( entry.created() ) );
      assertTrue( Files.readString( copy ).contains( "urn:samm:org.example.a:1.1.0#" ) );
      assertEquals( original, Files.readString( provider ) );
   }

   @Test
   void renameFileKeepsTheContent() throws IOException {
      final Path provider = providerA( defaultWorkspace );

      final RefactoringHistoryEntry entry = service.apply( new RefactoringRequest( RefactoringType.RENAME_FILE, null, "Renamed.ttl",
            "org.example.a", "1.0.0", "Provider.ttl", null, null, null, null, null, null ) );

      final Path renamed = provider.resolveSibling( "Renamed.ttl" );
      assertEquals( List.of( renamed.toString() ), paths( entry.created() ) );
      assertFalse( Files.exists( provider ) );
      assertTrue( Files.readString( renamed ).contains( "sharedProperty" ) );
   }

   @Test
   void refactoringsChangingFilesOfGithubSourcesAreBlocked() throws IOException {
      final Path provider = providerA( defaultWorkspace );
      final GithubSourceConfig source = configService.addGithubSource(
            new GithubSourceConfig( null, null, "eclipse-esmf", "esmf-sdk", null, "main", null, null ) ).githubSources().getLast();
      final Path consumer = consumerB( configService.getGithubSourceDirectory( source ) );
      final String original = Files.readString( provider );

      final RefactoringRequest request = renameElement( SHARED_PROPERTY, "renamedProperty" );
      final RefactoringPreview preview = service.preview( request );

      assertEquals( List.of( consumer.toString() ), paths( preview.blocked() ) );
      assertFalse( preview.applicable() );
      assertThrows( ReadOnlySourceException.class, () -> service.apply( request ) );
      assertEquals( original, Files.readString( provider ) );
   }

   @Test
   void rejectsInvalidRequests() {
      providerA( defaultWorkspace );
      write( defaultWorkspace, "org.example.a", "1.0.0", "Other.ttl", """
            @prefix : <urn:samm:org.example.a:1.0.0#> .
            :otherProperty a samm:Property ; samm:characteristic samm-c:Text .
            """ );

      assertThrows( NoSuchElementException.class, () -> service.preview( renameElement( "urn:samm:org.example.a:1.0.0#unknown", "x" ) ) );
      assertThrows( RefactoringException.class, () -> service.preview( renameElement( SHARED_PROPERTY, "otherProperty" ) ) );
      assertThrows( IllegalArgumentException.class, () -> service.preview( renameElement( "not a urn", "x" ) ) );
      assertThrows( IllegalArgumentException.class, () -> service.preview( renameElement( SHARED_PROPERTY, null ) ) );
      assertThrows( IllegalArgumentException.class, () -> service.preview( new RefactoringRequest( RefactoringType.RENAME_FILE, null,
            "../../Escaped.ttl", "org.example.a", "1.0.0", "Provider.ttl", null, null, null, null, null, null ) ) );
      assertThrows( RefactoringException.class, () -> service.apply( new RefactoringRequest( RefactoringType.RENAME_FILE, null,
            "Other.ttl", "org.example.a", "1.0.0", "Provider.ttl", null, null, null, null, null, null ) ) );
   }

   private static RefactoringRequest renameElement( final String urn, final String newName ) {
      return new RefactoringRequest( RefactoringType.RENAME_ELEMENT, urn, newName, null, null, null, null, null, null, null, null, null );
   }

   private static RefactoringRequest moveElement( final String urn, final String namespace, final String version, final String fileName ) {
      return new RefactoringRequest( RefactoringType.MOVE_ELEMENT, urn, null, null, null, null, null, namespace, version, fileName, null,
            null );
   }

   private static List<String> paths( final List<ChangedFile> files ) {
      return files.stream().map( ChangedFile::path ).toList();
   }
}
