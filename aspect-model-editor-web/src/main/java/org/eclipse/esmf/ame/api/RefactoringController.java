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

package org.eclipse.esmf.ame.api;

import java.util.List;

import org.eclipse.esmf.ame.services.models.refactoring.RefactoringHistoryEntry;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringPreview;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringProgress;
import org.eclipse.esmf.ame.services.models.refactoring.RefactoringRequest;
import org.eclipse.esmf.ame.services.refactoring.RefactoringService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;

/**
 * Controller for refactorings that change several Aspect Model files at once, e.g. renaming an element including all
 * references to it, and for reverting them.
 */
@Controller( "refactoring" )
@Produces( MediaType.APPLICATION_JSON )
public class RefactoringController {
   private final RefactoringService refactoringService;

   public RefactoringController( final RefactoringService refactoringService ) {
      this.refactoringService = refactoringService;
   }

   /**
    * Calculates which files a refactoring changes without changing anything.
    *
    * @param request the refactoring
    * @return HttpResponse containing the files that would be created, modified and removed
    */
   @Post( "/preview" )
   public HttpResponse<RefactoringPreview> preview( @Body final RefactoringRequest request ) {
      return HttpResponse.ok( refactoringService.preview( request ) );
   }

   /**
    * Applies a refactoring.
    *
    * @param request the refactoring
    * @return HttpResponse containing the applied refactoring, which can be reverted by its id
    */
   @Post( "/apply" )
   public HttpResponse<RefactoringHistoryEntry> apply( @Body final RefactoringRequest request ) {
      return HttpResponse.ok( refactoringService.apply( request ) );
   }

   /**
    * Returns the progress of the running preview or refactoring, so that the editor can show it for large workspaces.
    *
    * @return HttpResponse containing the progress
    */
   @Get( "/progress" )
   public HttpResponse<RefactoringProgress> getProgress() {
      return HttpResponse.ok( refactoringService.getProgress() );
   }

   /**
    * Lists the refactorings that can be reverted, the latest first.
    *
    * @return HttpResponse containing the refactoring history
    */
   @Get( "/history" )
   public HttpResponse<List<RefactoringHistoryEntry>> getHistory() {
      return HttpResponse.ok( refactoringService.getHistory() );
   }

   /**
    * Reverts a refactoring.
    *
    * @param id the id of the refactoring
    * @param force whether to revert even if affected files were changed after the refactoring
    * @return HttpResponse containing the reverted refactoring
    */
   @Post( "/history/{id}/revert" )
   public HttpResponse<RefactoringHistoryEntry> revert( @PathVariable final String id,
         @QueryValue( defaultValue = "false" ) final boolean force ) {
      return HttpResponse.ok( refactoringService.revert( id, force ) );
   }
}
