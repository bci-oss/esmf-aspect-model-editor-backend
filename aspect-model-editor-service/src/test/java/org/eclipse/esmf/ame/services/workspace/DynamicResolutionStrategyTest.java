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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.esmf.aspectmodel.loader.AspectModelLoader;
import org.eclipse.esmf.aspectmodel.resolver.exceptions.ModelResolutionException;
import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;
import org.eclipse.esmf.metamodel.AspectModel;

import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicResolutionStrategyTest {

   @TempDir
   Path tempDir;

   private Path ws1;
   private Path ws2;
   private WorkspaceConfigService workspaceConfigService;
   private DynamicResolutionStrategy strategy;
   private AspectModelLoader loader;

   @BeforeEach
   void setUp() throws IOException {
      ws1 = tempDir.resolve( "workspace-1" );
      ws2 = tempDir.resolve( "workspace-2" );
      Files.createDirectories( ws1 );
      Files.createDirectories( ws2 );

      final Path configFilePath = tempDir.resolve( "workspaces.json" );
      workspaceConfigService = new WorkspaceConfigService( configFilePath, ws1, JsonMapper.createDefault() );
      workspaceConfigService.addWorkspace( ws2.toString() );

      strategy = new DynamicResolutionStrategy( workspaceConfigService );
      loader = new AspectModelLoader( strategy );
   }

   @Test
   void testResolvesAcrossMultipleWorkspaces() throws IOException {
      // Place model A in ws1
      final Path modelADir = ws1.resolve( "com.example.one/1.0.0" );
      Files.createDirectories( modelADir );
      final String ttlA = """
            @prefix samm: <urn:samm:org.eclipse.esmf.samm:meta-model:2.2.0#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            @prefix : <urn:samm:com.example.one:1.0.0#> .

            :ModelA a samm:Aspect ;
               samm:name "ModelA" ;
               samm:properties () ;
               samm:operations () .
            """;
      Files.writeString( modelADir.resolve( "ModelA.ttl" ), ttlA );

      // Place model B in ws2
      final Path modelBDir = ws2.resolve( "com.example.two/1.0.0" );
      Files.createDirectories( modelBDir );
      final String ttlB = """
            @prefix samm: <urn:samm:org.eclipse.esmf.samm:meta-model:2.2.0#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            @prefix : <urn:samm:com.example.two:1.0.0#> .

            :ModelB a samm:Aspect ;
               samm:name "ModelB" ;
               samm:properties () ;
               samm:operations () .
            """;
      Files.writeString( modelBDir.resolve( "ModelB.ttl" ), ttlB );

      // Resolve ModelA from ws1 via AspectModelLoader
      final AspectModelUrn urnA = AspectModelUrn.fromUrn( "urn:samm:com.example.one:1.0.0#ModelA" );
      final AspectModel resultA = loader.load( urnA );
      assertNotNull( resultA, "Should resolve ModelA from ws1" );

      // Resolve ModelB from ws2 via AspectModelLoader
      final AspectModelUrn urnB = AspectModelUrn.fromUrn( "urn:samm:com.example.two:1.0.0#ModelB" );
      final AspectModel resultB = loader.load( urnB );
      assertNotNull( resultB, "Should resolve ModelB from ws2" );

      // Non-existent model should fail with ModelResolutionException
      final AspectModelUrn urnNonExistent = AspectModelUrn.fromUrn( "urn:samm:com.example.three:1.0.0#Missing" );
      assertThrows( ModelResolutionException.class, () -> loader.load( urnNonExistent ) );

      // listContents should find both models across the two workspaces
      final List<URI> allContents = strategy.listContents().toList();
      assertTrue( allContents.stream().anyMatch( uri -> uri.toString().contains( "ModelA.ttl" ) ) );
      assertTrue( allContents.stream().anyMatch( uri -> uri.toString().contains( "ModelB.ttl" ) ) );
   }
}
