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
import static org.eclipse.esmf.ame.services.workspace.WorkspaceTestFiles.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

import org.eclipse.esmf.ame.model.UnreadableModelFile;
import org.eclipse.esmf.ame.services.models.workspace.ElementUsage;
import org.eclipse.esmf.ame.services.models.workspace.ElementUsageReport;
import org.eclipse.esmf.ame.services.models.workspace.GithubSourceConfig;
import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;

import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Finding the usages of model elements across several workspaces and read-only GitHub sources.
 */
class ElementUsageServiceTest {
   private static final AspectModelUrn SHARED_PROPERTY = AspectModelUrn.fromUrn( "urn:samm:org.example.a:1.0.0#sharedProperty" );
   private static final AspectModelUrn SHARED_CHARACTERISTIC = AspectModelUrn.fromUrn(
         "urn:samm:org.example.a:1.0.0#SharedCharacteristic" );

   @TempDir
   Path tempDir;

   private Path defaultWorkspace;
   private Path second;
   private WorkspaceConfigService configService;
   private ElementUsageService service;

   @BeforeEach
   void setUp() {
      defaultWorkspace = tempDir.resolve( "models" ).toAbsolutePath().normalize();
      second = tempDir.resolve( "second" ).toAbsolutePath().normalize();
      configService = new WorkspaceConfigService( tempDir.resolve( "workspaces.json" ), defaultWorkspace, JsonMapper.createDefault() );
      configService.addWorkspace( second.toString() );
      service = new ElementUsageService( new WorkspaceReferenceService( defaultWorkspace, configService ) );
   }

   @Test
   void usagesInOtherWorkspacesAreExternal() {
      providerA( defaultWorkspace );
      final Path consumer = consumerB( second );

      final ElementUsageReport report = service.findUsages( SHARED_PROPERTY );

      assertEquals( SHARED_PROPERTY.toString(), report.urn() );
      assertEquals( List.of( new ElementUsage( "urn:samm:org.example.b:1.0.0#Consumer", "org.example.b", "1.0.0", "Consumer.ttl",
            consumer.toString(), false, true ) ), report.usages() );
      assertTrue( report.usedExternally() );
      assertTrue( report.unreadable().isEmpty() );
   }

   @Test
   void usagesInTheDefiningFileAreNotExternal() {
      final Path provider = providerA( defaultWorkspace );

      final ElementUsageReport report = service.findUsages( SHARED_CHARACTERISTIC );

      assertEquals( List.of( new ElementUsage( SHARED_PROPERTY.toString(), "org.example.a", "1.0.0", "Provider.ttl",
            provider.toString(), false, false ) ), report.usages() );
      assertFalse( report.usedExternally() );
   }

   @Test
   void indirectUsagesWithinAFileAreFoundAsWell() {
      write( defaultWorkspace, "org.example.a", "1.0.0", "Aspect.ttl", """
            @prefix : <urn:samm:org.example.a:1.0.0#> .
            :Aspect a samm:Aspect ; samm:properties ( :property ) ; samm:operations ( ) .
            :property a samm:Property ; samm:characteristic :Characteristic .
            :Characteristic a samm:Characteristic ; samm:dataType xsd:string .
            """ );

      final List<String> users = service.findUsages( AspectModelUrn.fromUrn( "urn:samm:org.example.a:1.0.0#Characteristic" ) )
            .usages().stream().map( ElementUsage::urn ).toList();

      assertEquals( List.of( "urn:samm:org.example.a:1.0.0#Aspect", "urn:samm:org.example.a:1.0.0#property" ), users );
   }

   @Test
   void usagesInGithubSourcesAreReadOnly() {
      providerA( defaultWorkspace );
      final GithubSourceConfig source = configService.addGithubSource(
            new GithubSourceConfig( null, null, "eclipse-esmf", "esmf-sdk", null, "main", null, null ) ).githubSources().getLast();
      consumerB( configService.getGithubSourceDirectory( source ) );

      final ElementUsage usage = service.findUsages( SHARED_PROPERTY ).usages().getFirst();

      assertTrue( usage.readOnly() );
      assertTrue( usage.external() );
   }

   @Test
   void unreadableFilesAreReportedInsteadOfFailing() {
      providerA( defaultWorkspace );
      write( second, "org.example.c", "1.0.0", "Broken.ttl", ":Broken a samm:Aspect ; samm:properties ( " );

      final ElementUsageReport report = service.findUsages( SHARED_PROPERTY );

      assertTrue( report.usages().isEmpty() );
      assertEquals( List.of( "Broken.ttl" ), report.unreadable().stream().map( UnreadableModelFile::fileName ).toList() );
   }

   @Test
   void undefinedElementsAreNotFound() {
      providerA( defaultWorkspace );

      assertThrows( NoSuchElementException.class,
            () -> service.findUsages( AspectModelUrn.fromUrn( "urn:samm:org.example.a:1.0.0#missing" ) ) );
   }
}
