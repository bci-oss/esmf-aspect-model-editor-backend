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
 * Creates files from the Aspect Model open in the editor, e.g. documentation or code. The model is passed as a copy:
 * generators cannot change the model in the editor. The user interface of the plugin decides what happens with the
 * files, e.g. lets the user save them.
 */
public interface ModelGenerator {
   /**
    * The id the frontend calls the generator with; unique within the plugin. Letters, digits, {@code .}, {@code _} and
    * {@code -} are allowed.
    *
    * @return the id of the generator
    */
   String id();

   /**
    * Creates the files.
    *
    * @param source the Aspect Model open in the editor and the options chosen in the frontend
    * @return the generated files
    * @throws PluginException if no files can be generated, e.g. because the model lacks information; the message is shown
    *       to the user
    */
   GenerationResult generate( GenerationSource source ) throws PluginException;
}
