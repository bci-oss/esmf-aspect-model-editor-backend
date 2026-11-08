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

/**
 * Converts a file of another format into Aspect Models. The frontend of the plugin uploads the file and decides what
 * happens with the result, e.g. opens it in the editor.
 */
public interface ModelImporter {
   /**
    * The id the frontend calls the importer with; unique within the plugin. Letters, digits, {@code .}, {@code _} and
    * {@code -} are allowed.
    *
    * @return the id of the importer
    */
   String id();

   /**
    * Converts the file.
    *
    * @param source the uploaded file and the options chosen in the frontend
    * @return the resulting Aspect Models
    * @throws PluginException if the file cannot be converted; the message is shown to the user
    */
   ImportResult importModel( ImportSource source ) throws PluginException;
}
