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

import java.util.List;
import java.util.Optional;

/**
 * Read access to the models of the workspaces, e.g. to check whether an imported model would replace an existing one.
 */
public interface WorkspaceAccess {
   /**
    * @return all model files of the workspaces
    */
   List<ModelFile> files();

   /**
    * Reads a model file.
    *
    * @param file the file to read
    * @return the RDF/Turtle content, or empty if the file does not exist
    */
   Optional<String> read( ModelFile file );

   /**
    * A model file in a workspace.
    *
    * @param namespace the namespace, e.g. {@code org.example}
    * @param version the version of the namespace, e.g. {@code 1.0.0}
    * @param fileName the name of the file, e.g. {@code Movement.ttl}
    */
   record ModelFile( String namespace, String version, String fileName ) {}
}
