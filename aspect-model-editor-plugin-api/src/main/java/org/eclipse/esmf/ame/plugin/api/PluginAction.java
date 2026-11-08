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
 * A free-form operation of a plugin, called by its frontend with a JSON payload.
 * <p>
 * Payload and result are passed as JSON text, so plugins are free to use any JSON library.
 * </p>
 */
public interface PluginAction {
   /**
    * The id the frontend calls the action with; unique within the plugin. Letters, digits, {@code .}, {@code _} and
    * {@code -} are allowed.
    *
    * @return the id of the action
    */
   String id();

   /**
    * Handles a call of the action.
    *
    * @param payload the JSON payload sent by the frontend; {@code "null"} if there is none
    * @return the JSON result returned to the frontend
    * @throws PluginException if the call cannot be handled; the message is shown to the user
    */
   String handle( String payload ) throws PluginException;
}
