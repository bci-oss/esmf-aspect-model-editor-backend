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
package org.eclipse.esmf.ame.ai.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.esmf.ame.ai.TestDirectories;
import org.eclipse.esmf.ame.ai.attachment.AiAttachmentStore;
import org.eclipse.esmf.ame.ai.model.attachment.AiAttachment;
import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;
import org.eclipse.esmf.ame.ai.model.chat.AiContextModel;
import org.eclipse.esmf.ame.ai.model.conversation.AiMessageContextRef;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService.WorkspaceFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiContextResolverTest {
   private Path directory;
   private Path workspace;
   private Path github;
   private final List<WorkspaceFile> files = new ArrayList<>();
   private AiAttachmentStore attachmentStore;
   private WorkspaceReferenceService workspaceReferenceService;
   private AiContextResolver resolver;

   @BeforeEach
   void setUp() throws IOException {
      directory = TestDirectories.create( "context" );
      workspace = Files.createDirectories( directory.resolve( "workspace" ) );
      github = Files.createDirectories( directory.resolve( "github-fleet" ) );
      attachmentStore = new AiAttachmentStore();
      workspaceReferenceService = mock( WorkspaceReferenceService.class );
      when( workspaceReferenceService.listWorkspaceFiles() ).thenReturn( files );
      when( workspaceReferenceService.isReadOnly( any() ) ).thenAnswer( call -> call.<Path> getArgument( 0 ).startsWith( github ) );
      resolver = new AiContextResolver( attachmentStore, workspaceReferenceService );
      file( workspace, "org.example", "1.0.0", "Movement.ttl", "# movement" );
      file( workspace, "org.example", "1.0.0", "Position.ttl", "# position" );
      file( workspace, "org.example", "2.0.0", "Movement.ttl", "# movement 2" );
      file( github, "org.fleet", "1.0.0", "Vehicle.ttl", "# vehicle" );
   }

   @AfterEach
   void tearDown() {
      TestDirectories.delete( directory );
   }

   private void file( final Path root, final String namespace, final String version, final String fileName, final String content )
         throws IOException {
      final Path path = Files.createDirectories( root.resolve( namespace ).resolve( version ) ).resolve( fileName );
      Files.writeString( path, content, StandardCharsets.UTF_8 );
      files.add( new WorkspaceFile( path, namespace, version, fileName, root ) );
   }

   private static AiChatContext context( final List<String> attachmentIds, final AiContextModel... models ) {
      return new AiChatContext( null, null, null, null, null, null, null, null, null, null, null, attachmentIds,
            models.length == 0 ? null : Arrays.asList( models ) );
   }

   private static List<String> labels( final AiContextResolver.Resolved resolved ) {
      return resolved.modelFiles().stream().map( AiContextResolver.ModelFile::label ).toList();
   }

   @Test
   void resolvesNothingForAnEmptyContext() {
      assertSame( AiContextResolver.Resolved.EMPTY, resolver.resolve( AiChatContext.EMPTY ) );
      assertTrue( AiContextResolver.Resolved.EMPTY.isEmpty() );
      assertSame( AiContextResolver.Resolved.EMPTY, resolver.resolve( context( List.of( " " ), new AiContextModel( null, " ", null,
            null ) ) ) );
   }

   @Test
   void resolvesAttachmentsAndReportsMissingOnes() {
      final AiAttachment attachment = attachmentStore.add( "spec.md", "The spec".getBytes( StandardCharsets.UTF_8 ) );
      final AiContextResolver.Resolved resolved = resolver.resolve( context( List.of( attachment.id(), "gone", attachment.id() ) ) );

      assertEquals( 1, resolved.attachments().size() );
      assertEquals( "The spec", resolved.attachments().getFirst().text() );
      assertEquals( List.of( "gone" ), resolved.missingAttachmentIds() );
      assertEquals( List.of( new AiMessageContextRef( AiMessageContextRef.FILE, "spec.md", attachment.id() ) ), resolved.refs() );
      assertFalse( resolved.isEmpty() );
   }

   @Test
   void resolvesAFileANamespaceVersionANamespaceAndASource() {
      assertEquals( List.of( "org.example:1.0.0/Movement.ttl" ), labels( resolver.resolve( context( null,
            new AiContextModel( workspace.toString(), "org.example", "1.0.0", "Movement.ttl" ) ) ) ) );
      assertEquals( List.of( "org.example:1.0.0/Movement.ttl", "org.example:1.0.0/Position.ttl" ), labels( resolver.resolve(
            context( null, new AiContextModel( null, "org.example", "1.0.0", null ) ) ) ) );
      assertEquals( 3, labels( resolver.resolve( context( null, new AiContextModel( null, "org.example", null, null ) ) ) ).size() );
      final AiContextResolver.Resolved source = resolver.resolve( context( null, new AiContextModel( github + "/", null, null, null ) ) );
      assertEquals( List.of( "org.fleet:1.0.0/Vehicle.ttl" ), labels( source ) );
      assertTrue( source.modelFiles().getFirst().readOnly() );
      assertEquals( "# vehicle", source.modelFiles().getFirst().content() );
      assertEquals( List.of( new AiMessageContextRef( AiMessageContextRef.SOURCE, "github-fleet", null ) ), source.refs() );
   }

   @Test
   void neverReadsFilesOutsideTheListedSources() {
      final AiContextResolver.Resolved resolved = resolver.resolve( context( null,
            new AiContextModel( directory.toString(), null, null, null ),
            new AiContextModel( workspace.toString(), "../../etc", "1.0.0", "passwd" ),
            new AiContextModel( "\u0000invalid", null, null, null ) ) );
      assertTrue( resolved.modelFiles().isEmpty() );
      assertEquals( 3, resolved.refs().size() );
   }

   @Test
   void deduplicatesFilesSelectedTwice() {
      final AiContextResolver.Resolved resolved = resolver.resolve( context( null,
            new AiContextModel( null, "org.example", "1.0.0", "Movement.ttl" ),
            new AiContextModel( workspace.toString(), "org.example", "1.0.0", null ) ) );
      assertEquals( List.of( "org.example:1.0.0/Movement.ttl", "org.example:1.0.0/Position.ttl" ), labels( resolved ) );
   }

   @Test
   void inlinesModelFilesOnlyWithinTheBudget() throws IOException {
      file( workspace, "org.big", "1.0.0", "Big.ttl", "x".repeat( AiPrompts.MAX_MODEL_LENGTH - 5 ) );
      file( workspace, "org.big", "1.0.0", "Small.ttl", "0123456789" );
      file( workspace, "org.big", "1.0.0", "Tiny.ttl", "abc" );
      final List<AiContextResolver.ModelFile> resolved = resolver.resolve( context( null,
            new AiContextModel( null, "org.big", null, null ) ) ).modelFiles();
      assertNotNull( resolved.get( 0 ).content() );
      assertNull( resolved.get( 1 ).content() );
      assertEquals( "abc", resolved.get( 2 ).content() );
   }

   @Test
   void limitsTheNumberOfModelFiles() throws IOException {
      for ( int i = 0; i < AiContextResolver.MAX_MODEL_FILES + 3; i++ ) {
         file( workspace, "org.many", "1.0.0", "M" + i + ".ttl", "" );
      }
      final AiContextResolver.Resolved resolved = resolver.resolve( context( null, new AiContextModel( null, "org.many", null, null ) ) );
      assertEquals( AiContextResolver.MAX_MODEL_FILES, resolved.modelFiles().size() );
      assertEquals( 3, resolved.omittedModelFiles() );
   }

   @Test
   void survivesFailingWorkspaceServices() throws IOException {
      doThrow( new IllegalStateException( "boom" ) ).when( workspaceReferenceService ).isReadOnly( any() );
      Files.delete( files.get( 1 ).path() );
      final AiContextResolver.Resolved resolved = resolver.resolve( context( null,
            new AiContextModel( null, "org.example", "1.0.0", null ) ) );
      assertFalse( resolved.modelFiles().getFirst().readOnly() );
      assertNull( resolved.modelFiles().get( 1 ).content() );

      doThrow( new IllegalStateException( "boom" ) ).when( workspaceReferenceService ).listWorkspaceFiles();
      assertTrue( resolver.resolve( context( null, new AiContextModel( null, "org.example", null, null ) ) ).modelFiles().isEmpty() );
   }

   @Test
   void labelsTheContextReferences() {
      assertEquals( new AiMessageContextRef( AiMessageContextRef.MODEL, "org.example:1.0.0/Movement.ttl", null ),
            AiContextResolver.ref( new AiContextModel( null, "org.example", "1.0.0", "Movement.ttl" ) ) );
      assertEquals( new AiMessageContextRef( AiMessageContextRef.NAMESPACE, "org.example:1.0.0", null ),
            AiContextResolver.ref( new AiContextModel( "/ws", "org.example", "1.0.0", null ) ) );
      assertEquals( new AiMessageContextRef( AiMessageContextRef.NAMESPACE, "org.example", null ),
            AiContextResolver.ref( new AiContextModel( null, "org.example", null, "Movement.ttl" ) ) );
      assertEquals( "models", AiContextResolver.ref( new AiContextModel( "C:\\Users\\me\\models\\", null, null, null ) ).label() );
      assertEquals( "/", AiContextResolver.ref( new AiContextModel( "/", null, null, null ) ).label() );
   }
}
