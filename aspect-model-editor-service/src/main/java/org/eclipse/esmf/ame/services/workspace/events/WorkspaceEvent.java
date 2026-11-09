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
package org.eclipse.esmf.ame.services.workspace.events;

import java.util.Map;

import org.eclipse.esmf.ame.services.models.workspace.WorkspaceFilesChanged;

/**
 * An event pushed to the clients observing the workspaces.
 *
 * @param name the event name, one of {@link #READY}, {@link #FILES_CHANGED} and {@link #HEARTBEAT}
 * @param data the event payload
 */
public record WorkspaceEvent( String name, Object data ) {
   /** Sent once to every new subscriber; a client that reconnects must assume that it missed changes. */
   public static final String READY = "ready";
   /** Model files were changed by another program. The payload is a {@link WorkspaceFilesChanged}. */
   public static final String FILES_CHANGED = "files-changed";
   /** Keeps idle connections open and lets the server detect closed ones. */
   public static final String HEARTBEAT = "heartbeat";

   public static WorkspaceEvent ready() {
      return new WorkspaceEvent( READY, Map.of() );
   }

   public static WorkspaceEvent filesChanged( final WorkspaceFilesChanged changes ) {
      return new WorkspaceEvent( FILES_CHANGED, changes );
   }

   public static WorkspaceEvent heartbeat() {
      return new WorkspaceEvent( HEARTBEAT, Map.of() );
   }
}
