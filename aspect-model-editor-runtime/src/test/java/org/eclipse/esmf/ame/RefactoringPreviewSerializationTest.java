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

package org.eclipse.esmf.ame;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.esmf.ame.services.models.refactoring.ChangedFile;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringPreview;

import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * The editor only enables applying a refactoring if the preview says it is applicable, so the derived flag has to be
 * part of the response.
 */
@MicronautTest
class RefactoringPreviewSerializationTest {
   private static final ChangedFile MOVEMENT = new ChangedFile( "org.eclipse.examples", "1.0.0", "Movement.ttl",
         "/models/org.eclipse.examples/1.0.0/Movement.ttl", false );

   @Inject
   JsonMapper jsonMapper;

   @Test
   void applicablePreviewsAreSerializedAsApplicable() throws IOException {
      final RefactoringPreview preview = new RefactoringPreview( "Rename", "", List.of(), List.of( MOVEMENT ), List.of(), List.of() );

      assertEquals( true, serialize( preview ).get( "applicable" ) );
   }

   @Test
   void blockedPreviewsAreSerializedAsNotApplicable() throws IOException {
      final RefactoringPreview preview = new RefactoringPreview( "Rename", "", List.of(), List.of(), List.of(), List.of( MOVEMENT ) );

      assertEquals( false, serialize( preview ).get( "applicable" ) );
   }

   private Map<?, ?> serialize( final RefactoringPreview preview ) throws IOException {
      return jsonMapper.readValue( jsonMapper.writeValueAsString( preview ), Map.class );
   }
}
