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
package org.eclipse.esmf.ame.ai.tools;

import static org.eclipse.esmf.ame.ai.tools.ToolOutput.wrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

import org.eclipse.esmf.ame.ai.attachment.AiAttachmentStore;
import org.eclipse.esmf.ame.ai.chat.AiPrompts;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService;
import org.eclipse.esmf.ame.services.workspace.WorkspaceReferenceService.WorkspaceFile;

/**
 * The tools reading the context the user added: attached files and workspace model files.
 */
final class ContextReadTools {
   private final AiAttachmentStore attachmentStore;
   private final WorkspaceReferenceService workspaceReferenceService;

   ContextReadTools( final AiAttachmentStore attachmentStore, final WorkspaceReferenceService workspaceReferenceService ) {
      this.attachmentStore = attachmentStore;
      this.workspaceReferenceService = workspaceReferenceService;
   }

   AiToolResult readAttachment( final String id, final int part ) {
      final Optional<AiAttachmentStore.Stored> stored = attachmentStore.get( id );
      if ( stored.isEmpty() ) {
         return AiToolResult.failure( "The attached file '" + id + "' is no longer available. Ask the user to attach it again.",
               "Attachment not available" );
      }
      final String text = stored.get().text();
      final String name = stored.get().info().name();
      final int parts = AiPrompts.parts( text.length() );
      if ( part < 1 || part > parts ) {
         throw new IllegalArgumentException( "The part must be between 1 and " + parts );
      }
      final int start = ( part - 1 ) * AiPrompts.ATTACHMENT_PART_LENGTH;
      final String content = text.substring( start, Math.min( text.length(), start + AiPrompts.ATTACHMENT_PART_LENGTH ) );
      return AiToolResult.ok( "<attachment name=\"" + name.replace( "\"", "" ) + "\" part=\"" + part + "/" + parts + "\">\n" + content
            + "\n</attachment>", "Read " + name + ( parts == 1 ? "" : " (part " + part + "/" + parts + ")" ) );
   }

   AiToolResult readModelFile( final String namespace, final String version, final String fileName ) {
      final Optional<WorkspaceFile> file = workspaceReferenceService.listWorkspaceFiles().stream()
            .filter( candidate -> candidate.namespace().equals( namespace ) && candidate.version().equals( version )
                  && candidate.fileName().equals( fileName ) )
            .findFirst();
      if ( file.isEmpty() ) {
         return AiToolResult.failure( "The model file " + namespace + ":" + version + "/" + fileName
               + " does not exist in the workspace. Use listWorkspaceModels to see the files.", "File not found" );
      }
      try {
         final String content = Files.readString( file.get().path(), StandardCharsets.UTF_8 );
         return AiToolResult.ok( wrap( content, fileName ), "Read " + fileName );
      } catch ( final IOException exception ) {
         return AiToolResult.failure( "The model file could not be read: " + exception.getMessage(), "Read failed" );
      }
   }
}
