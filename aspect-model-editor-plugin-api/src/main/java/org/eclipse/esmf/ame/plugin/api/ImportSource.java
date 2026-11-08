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

package org.eclipse.esmf.ame.plugin.api;

import java.util.Map;
import java.util.Objects;

/**
 * A file to import.
 *
 * @param fileName the name of the uploaded file
 * @param content the content of the file
 * @param options the options chosen in the frontend of the plugin
 */
public record ImportSource( String fileName, byte[] content, Map<String, String> options ) {
   public ImportSource {
      Objects.requireNonNull( fileName, "fileName" );
      Objects.requireNonNull( content, "content" );
      options = Map.copyOf( Objects.requireNonNull( options, "options" ) );
   }
}
