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

import static org.eclipse.esmf.ame.services.workspace.WorkspaceTestFiles.consumerB;
import static org.eclipse.esmf.ame.services.workspace.WorkspaceTestFiles.providerA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.esmf.ame.exceptions.FileNotFoundException;
import org.eclipse.esmf.ame.exceptions.ModelReferencedException;
import org.eclipse.esmf.ame.exceptions.ReadOnlySourceException;
import org.eclipse.esmf.ame.repository.AspectModelRepository;
import org.eclipse.esmf.ame.services.AspectModelReader;
import org.eclipse.esmf.ame.services.FileLoadErrorHandler;
import org.eclipse.esmf.ame.services.ModelService;
import org.eclipse.esmf.ame.services.PackageService;
import org.eclipse.esmf.ame.services.file.FileOperations;
import org.eclipse.esmf.ame.services.file.FilePathResolver;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceConfig;
import org.eclipse.esmf.ame.services.models.workspace.ModelSource;
import org.eclipse.esmf.ame.services.models.workspace.WorkspaceConfig;
import org.eclipse.esmf.ame.services.validation.ValidationOperations;
import org.eclipse.esmf.aspectmodel.loader.AspectModelLoader;
import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;
import org.eclipse.esmf.aspectmodel.validation.services.AspectModelValidator;

import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Several local workspaces combined with read-only GitHub sources.
 */
class MultiSourceWorkspaceTest {
   private static final GithubSourceConfig SDK_SOURCE = new GithubSourceConfig( null, null, "eclipse-esmf", "esmf-sdk", null, "main",
         "/core/models/", null );

   @TempDir
   Path tempDir;

   private Path defaultWorkspace;
   private Path second;
   private WorkspaceConfigService configService;
   private WorkspaceReferenceService referenceService;
   private WorkspaceCleanupService cleanupService;

   @BeforeEach
   void setUp() {
      defaultWorkspace = tempDir.resolve( "models" ).toAbsolutePath().normalize();
      second = tempDir.resolve( "second" ).toAbsolutePath().normalize();
      configService = new WorkspaceConfigService( tempDir.resolve( "workspaces.json" ), defaultWorkspace, JsonMapper.createDefault() );
      configService.addWorkspace( second.toString() );
      referenceService = new WorkspaceReferenceService( defaultWorkspace, configService );
      cleanupService = new WorkspaceCleanupService( referenceService, new FileOperations(), new FilePathResolver(),
            mock( PackageService.class ) );
   }

   private Path addSyncedGithubSource() throws IOException {
      final GithubSourceConfig source = configService.addGithubSource( SDK_SOURCE ).githubSources().getLast();
      return Files.createDirectories( configService.getGithubSourceDirectory( source ) );
   }

   @Test
   void normalizesGithubSourcesAndDerivesTheId() {
      final GithubSourceConfig source = configService.addGithubSource( SDK_SOURCE ).githubSources().getLast();

      assertEquals( "eclipse-esmf_esmf-sdk_main_core-models", source.id() );
      assertEquals( GithubSourceConfig.DEFAULT_HOST, source.host() );
      assertEquals( GithubSourceConfig.BRANCH, source.refType() );
      assertEquals( "core/models", source.directory() );
      assertTrue( source.enabled() );
      assertEquals( "eclipse-esmf/esmf-sdk@main/core/models", source.displayName() );
      assertEquals( tempDir.resolve( "github" ).resolve( source.id() ).toAbsolutePath().normalize(),
            configService.getGithubSourceDirectory( source ) );
      assertThrows( IllegalArgumentException.class, () -> configService.addGithubSource( SDK_SOURCE ) );
   }

   @Test
   void rejectsInvalidGithubSources() {
      final List<GithubSourceConfig> invalid = List.of(
            new GithubSourceConfig( null, null, "..", "repo", null, "main", null, null ),
            new GithubSourceConfig( null, null, "owner", "re/po", null, "main", null, null ),
            new GithubSourceConfig( null, null, "owner", "repo", null, "../main", null, null ),
            new GithubSourceConfig( null, null, "owner", "repo", null, "-main", null, null ),
            new GithubSourceConfig( null, null, "owner", "repo", "commit", "main", null, null ),
            new GithubSourceConfig( null, null, "owner", "repo", null, "main", "models/../..", null ),
            new GithubSourceConfig( null, "evil.com/path", "owner", "repo", null, "main", null, null ) );
      invalid.forEach( source -> assertThrows( IllegalArgumentException.class, () -> configService.addGithubSource( source ),
            source::toString ) );
      assertTrue( configService.getConfig().githubSources().isEmpty() );
   }

   @Test
   void listsWorkspacesBeforeSynchronizedGithubSources() throws IOException {
      final GithubSourceConfig unsynced = configService.addGithubSource(
            new GithubSourceConfig( null, null, "owner", "other", "tag", "v1.0.0", null, null ) ).githubSources().getFirst();
      assertEquals( List.of( defaultWorkspace, second ), configService.getReadablePaths() );

      final Path github = addSyncedGithubSource();
      final List<ModelSource> sources = configService.getSources();

      assertEquals( List.of( defaultWorkspace, second, github ), configService.getReadablePaths() );
      assertTrue( sources.getFirst().defaultWorkspace() );
      assertFalse( sources.get( 1 ).readOnly() );
      assertTrue( sources.get( 2 ).readOnly() );
      assertEquals( ModelSource.GITHUB, sources.get( 2 ).type() );
      assertFalse( Files.exists( configService.getGithubSourceDirectory( unsynced ) ) );
   }

   @Test
   void disabledGithubSourcesAreNotResolved() throws IOException {
      final Path github = addSyncedGithubSource();
      final String id = configService.getConfig().githubSources().getFirst().id();
      configService.updateGithubSource( id, new GithubSourceConfig( id, null, "eclipse-esmf", "esmf-sdk", null, "main", "core/models",
            false ) );

      assertEquals( List.of( defaultWorkspace, second ), configService.getReadablePaths() );
      assertTrue( Files.isDirectory( github ), "disabling keeps the local copy" );
   }

   @Test
   void removingAGithubSourceDeletesItsLocalCopy() throws IOException {
      final Path github = addSyncedGithubSource();
      Files.writeString( github.resolve( "file.ttl" ), "" );

      configService.removeGithubSource( configService.getConfig().githubSources().getFirst().id() );

      assertFalse( Files.exists( github ) );
      assertTrue( configService.getConfig().githubSources().isEmpty() );
   }

   @Test
   void persistsGithubSourcesAndReadsVersionOneFiles() throws IOException {
      configService.addGithubSource( SDK_SOURCE );
      final WorkspaceConfigService reloaded = new WorkspaceConfigService( tempDir.resolve( "workspaces.json" ), defaultWorkspace,
            JsonMapper.createDefault() );
      assertEquals( configService.getConfig(), reloaded.getConfig() );

      final Path v1File = tempDir.resolve( "v1.json" );
      Files.writeString( v1File, "{\"workspaces\":[\"" + second.toString().replace( "\\", "\\\\" ) + "\"],\"activeWorkspace\":\""
            + second.toString().replace( "\\", "\\\\" ) + "\"}" );
      final WorkspaceConfig v1 = new WorkspaceConfigService( v1File, defaultWorkspace, JsonMapper.createDefault() ).getConfig();

      assertEquals( WorkspaceConfig.CURRENT_VERSION, v1.version() );
      assertEquals( List.of( defaultWorkspace.toString(), second.toString() ), v1.workspaces(), "the default workspace comes first" );
      assertTrue( v1.githubSources().isEmpty() );
   }

   @Test
   void rejectsNestedWorkspacesAndTheGithubCache() {
      assertThrows( IllegalArgumentException.class, () -> configService.addWorkspace( defaultWorkspace.resolve( "inner" ).toString() ) );
      assertThrows( IllegalArgumentException.class, () -> configService.addWorkspace( tempDir.toString() ) );
      assertThrows( IllegalArgumentException.class,
            () -> configService.addWorkspace( configService.getGithubCachePath().resolve( "x" ).toString() ) );
   }

   @Test
   void resetKeepsGithubSources() {
      configService.addGithubSource( SDK_SOURCE );
      configService.resetToDefault();

      assertEquals( List.of( defaultWorkspace ), configService.getWorkspacePaths() );
      assertEquals( 1, configService.getConfig().githubSources().size() );
   }

   @Test
   void resolvesTheTargetWorkspace() {
      assertEquals( defaultWorkspace, configService.resolveTargetWorkspace( null ) );
      assertEquals( second, configService.resolveTargetWorkspace( second.toString() ) );
      assertThrows( IllegalArgumentException.class, () -> configService.resolveTargetWorkspace( tempDir.resolve( "x" ).toString() ) );
   }

   @Test
   void savesExistingModelsWhereTheyAreAndNewModelsToTheTarget() throws IOException {
      final ModelService modelService = new ModelService( mock( AspectModelValidator.class ), mock( AspectModelLoader.class ),
            mock( AspectModelRepository.class ), mock( AspectModelReader.class ), new FilePathResolver(), mock( ValidationOperations.class ),
            defaultWorkspace, mock( FileLoadErrorHandler.class ), referenceService, configService );
      final AspectModelUrn urn = AspectModelUrn.fromUrn( "urn:samm:org.example.a:1.0.0#Provider" );
      final Path github = addSyncedGithubSource();

      assertEquals( second, modelService.resolveSaveLocation( urn, "Provider.ttl", second.toString() ) );
      assertEquals( defaultWorkspace, modelService.resolveSaveLocation( urn, "Provider.ttl", null ) );

      providerA( second );
      assertEquals( second, modelService.resolveSaveLocation( urn, "Provider.ttl", defaultWorkspace.toString() ),
            "an existing file stays in its workspace" );

      consumerB( github );
      final AspectModelUrn githubUrn = AspectModelUrn.fromUrn( "urn:samm:org.example.b:1.0.0#Consumer" );
      assertThrows( ReadOnlySourceException.class, () -> modelService.resolveSaveLocation( githubUrn, "Consumer.ttl", null ) );
   }

   @Test
   void locatesFilesInAllSources() throws IOException {
      final ModelService modelService = new ModelService( mock( AspectModelValidator.class ), mock( AspectModelLoader.class ),
            mock( AspectModelRepository.class ), mock( AspectModelReader.class ), new FilePathResolver(), mock( ValidationOperations.class ),
            defaultWorkspace, mock( FileLoadErrorHandler.class ), referenceService, configService );
      final Path github = addSyncedGithubSource();
      providerA( second );
      consumerB( github );

      assertEquals( second.resolve( "org.example.a/1.0.0/Provider.ttl" ).toAbsolutePath().normalize(),
            modelService.locateModelFile( "org.example.a", "1.0.0", "Provider.ttl", null ) );
      assertEquals( github.resolve( "org.example.b/1.0.0/Consumer.ttl" ).toAbsolutePath().normalize(),
            modelService.locateModelFile( "org.example.b", "1.0.0", "Consumer.ttl", null ) );
      assertEquals( github.resolve( "org.example.b/1.0.0/Consumer.ttl" ).toAbsolutePath().normalize(),
            modelService.locateModelFile( "org.example.b", "1.0.0", "Consumer.ttl", github.toString() ) );

      providerA( defaultWorkspace );
      assertEquals( second.resolve( "org.example.a/1.0.0/Provider.ttl" ).toAbsolutePath().normalize(),
            modelService.locateModelFile( "org.example.a", "1.0.0", "Provider.ttl", second.toString() ),
            "the given source wins over the resolution order" );
      assertThrows( FileNotFoundException.class,
            () -> modelService.locateModelFile( "org.example.b", "1.0.0", "Consumer.ttl", second.toString() ) );
      assertThrows( IllegalArgumentException.class,
            () -> modelService.locateModelFile( "org.example.a", "1.0.0", "Provider.ttl", "/not/configured" ) );
      assertThrows( IllegalArgumentException.class,
            () -> modelService.locateModelFile( "org.example.a", "1.0.0", "../Provider.ttl", null ) );
   }

   @Test
   void deletesANamespaceOnlyInItsWorkspace() {
      providerA( defaultWorkspace );
      providerA( second );
      consumerB( second );

      assertThrows( IllegalArgumentException.class, () -> cleanupService.deleteNamespace( "org.example.a", "1.0.0" ),
            "ambiguous without workspace" );
      assertTrue( cleanupService.checkNamespace( "org.example.a", "1.0.0", defaultWorkspace.toString() ).deletable(),
            "the copy in the second workspace still defines the elements" );

      assertEquals( 1, cleanupService.deleteNamespace( "org.example.a", "1.0.0", defaultWorkspace.toString() ) );
      assertFalse( Files.exists( defaultWorkspace.resolve( "org.example.a" ) ) );
      assertTrue( Files.exists( second.resolve( "org.example.a/1.0.0/Provider.ttl" ) ) );

      assertThrows( ModelReferencedException.class, () -> cleanupService.deleteNamespace( "org.example.a", "1.0.0" ) );
   }

   @Test
   void neverChangesGithubSources() throws IOException {
      final Path github = addSyncedGithubSource();
      providerA( github );

      assertTrue( configService.isReadOnly( github.resolve( "org.example.a/1.0.0/Provider.ttl" ) ) );
      assertThrows( ReadOnlySourceException.class, () -> cleanupService.deleteNamespace( "org.example.a", "1.0.0" ) );
      assertThrows( ReadOnlySourceException.class, () -> cleanupService.checkFile( "org.example.a", "1.0.0", "Provider.ttl" ) );
      assertThrows( IllegalArgumentException.class,
            () -> cleanupService.deleteNamespace( "org.example.a", "1.0.0", github.toString() ) );
      assertThrows( FileNotFoundException.class,
            () -> cleanupService.deleteNamespace( "org.example.a", "1.0.0", second.toString() ) );
      assertTrue( Files.exists( github.resolve( "org.example.a/1.0.0/Provider.ttl" ) ) );
   }

   @Test
   void consumersInGithubSourcesBlockTheDeletion() throws IOException {
      final Path github = addSyncedGithubSource();
      providerA( second );
      consumerB( github );

      assertFalse( cleanupService.checkNamespace( "org.example.a", "1.0.0" ).deletable() );
   }
}
