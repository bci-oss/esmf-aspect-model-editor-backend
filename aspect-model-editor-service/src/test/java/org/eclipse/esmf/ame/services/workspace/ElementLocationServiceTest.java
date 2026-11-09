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

import static org.eclipse.esmf.ame.services.workspace.WorkspaceTestFiles.PREFIXES;
import static org.eclipse.esmf.ame.services.workspace.WorkspaceTestFiles.consumerB;
import static org.eclipse.esmf.ame.services.workspace.WorkspaceTestFiles.providerA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.esmf.ame.services.models.workspace.ElementLocation;
import org.eclipse.esmf.aspectmodel.urn.AspectModelUrn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ElementLocationServiceTest {
   private static final int PREFIX_LINES = (int) PREFIXES.lines().count();

   @TempDir
   Path workspace;

   private ElementLocationService service;

   @BeforeEach
   void setUp() {
      service = new ElementLocationService( new WorkspaceReferenceService( workspace ) );
   }

   @Test
   void locatesTheDeclarationOfAnElement() {
      final Path provider = providerA( workspace );

      final Optional<ElementLocation> property = service.locate(
            AspectModelUrn.fromUrn( "urn:samm:org.example.a:1.0.0#sharedProperty" ) );
      final Optional<ElementLocation> characteristic = service.locate(
            AspectModelUrn.fromUrn( "urn:samm:org.example.a:1.0.0#SharedCharacteristic" ) );

      final String path = provider.toAbsolutePath().normalize().toString();
      assertEquals( Optional.of( new ElementLocation( path, PREFIX_LINES + 2, 1 ) ), property );
      assertEquals( Optional.of( new ElementLocation( path, PREFIX_LINES + 3, 1 ) ), characteristic );
   }

   @Test
   void findsTheDeclaringFileAmongSeveralNamespaces() {
      providerA( workspace );
      final Path consumer = consumerB( workspace );

      final Optional<ElementLocation> location = service.locate( AspectModelUrn.fromUrn( "urn:samm:org.example.b:1.0.0#Consumer" ) );

      assertTrue( location.isPresent() );
      assertEquals( consumer.toAbsolutePath().normalize().toString(), location.get().path() );
      assertEquals( PREFIX_LINES + 3, location.get().line() );
   }

   @Test
   void referencedButUndeclaredElementHasNoLocation() {
      providerA( workspace );
      consumerB( workspace );

      assertTrue( service.locate( AspectModelUrn.fromUrn( "urn:samm:org.example.b:1.0.0#sharedProperty" ) ).isEmpty() );
      assertTrue( service.locate( AspectModelUrn.fromUrn( "urn:samm:org.example.c:1.0.0#Unknown" ) ).isEmpty() );
   }

   @Test
   void preferredFileWinsIfSeveralFilesDeclareTheElement() {
      final String declaration = """
            @prefix : <urn:samm:org.example.e:1.0.0#> .

            :property1 a samm:Property ;
               samm:characteristic samm-c:Text .
            """;
      final Path first = WorkspaceTestFiles.write( workspace, "org.example.e", "1.0.0", "AspectA.ttl", declaration );
      final Path second = WorkspaceTestFiles.write( workspace, "org.example.e", "1.0.0", "AspectB.ttl", declaration );
      final AspectModelUrn urn = AspectModelUrn.fromUrn( "urn:samm:org.example.e:1.0.0#property1" );

      assertEquals( second.toAbsolutePath().normalize().toString(), service.locate( urn, "AspectB.ttl" ).orElseThrow().path() );
      assertEquals( first.toAbsolutePath().normalize().toString(), service.locate( urn, "AspectA.ttl" ).orElseThrow().path() );
      assertEquals( first.toAbsolutePath().normalize().toString(), service.locate( urn ).orElseThrow().path() );
   }

   @Test
   void preferredFileWithoutTheElementIsIgnored() {
      providerA( workspace );
      final Path consumer = consumerB( workspace );

      final Optional<ElementLocation> location = service.locate( AspectModelUrn.fromUrn( "urn:samm:org.example.b:1.0.0#Consumer" ),
            "Unknown.ttl" );

      assertEquals( consumer.toAbsolutePath().normalize().toString(), location.orElseThrow().path() );
   }

   @Test
   void indentedDeclarationReportsItsColumn() {
      final Path file = WorkspaceTestFiles.write( workspace, "org.example.d", "1.0.0", "Indented.ttl", """
            @prefix : <urn:samm:org.example.d:1.0.0#> .

               :indented a samm:Property ;
                  samm:characteristic samm-c:Text .
            """ );

      assertEquals( new ElementLocation( file.toAbsolutePath().normalize().toString(), PREFIX_LINES + 3, 4 ),
            ElementLocationService.locateIn( file, "urn:samm:org.example.d:1.0.0#indented" ) );
   }
}
