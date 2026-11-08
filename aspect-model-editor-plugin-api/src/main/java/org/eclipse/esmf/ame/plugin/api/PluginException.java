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

import java.io.Serial;

/**
 * Thrown by a plugin if a request cannot be handled. The message is shown to the user, so it should explain the problem
 * in a way the user can act upon, e.g. "The file contains no Aspect".
 */
public class PluginException extends Exception {
   @Serial
   private static final long serialVersionUID = 1L;

   public PluginException( final String message ) {
      super( message );
   }

   public PluginException( final String message, final Throwable cause ) {
      super( message, cause );
   }
}
