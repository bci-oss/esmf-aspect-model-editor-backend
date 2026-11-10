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
package org.eclipse.esmf.ame.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Test directories below {@code target}, so no files are written to the system temp directory.
 */
public final class TestDirectories {
   private TestDirectories() {
   }

   public static Path create( final String name ) {
      try {
         return Files.createDirectories( Path.of( "target", "ai-tests", name + "-" + UUID.randomUUID() ) ).toAbsolutePath();
      } catch ( final IOException exception ) {
         throw new UncheckedIOException( exception );
      }
   }

   public static void delete( final Path directory ) {
      if ( directory == null || !Files.exists( directory ) ) {
         return;
      }
      try ( final Stream<Path> files = Files.walk( directory ) ) {
         files.sorted( Comparator.reverseOrder() ).forEach( file -> file.toFile().delete() );
      } catch ( final IOException exception ) {
         throw new UncheckedIOException( exception );
      }
   }
}
