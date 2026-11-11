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

import org.eclipse.esmf.ame.ai.model.validation.AiValidationRequest;
import org.eclipse.esmf.ame.ai.model.validation.AiValidationResult;
import org.eclipse.esmf.ame.ai.tools.AiModelValidator;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

/**
 * Validates the proposals of the AI assistant before the user applies them.
 */
@Controller( "ai" )
@Produces( MediaType.APPLICATION_JSON )
@ExecuteOn( TaskExecutors.BLOCKING )
public class AiProposalController {
   private final AiModelValidator modelValidator;

   public AiProposalController( final AiModelValidator modelValidator ) {
      this.modelValidator = modelValidator;
   }

   /**
    * Validates the Turtle text of a proposal.
    *
    * @param request the Turtle text
    * @return the validation result
    */
   @Post( "/proposals/validate" )
   public HttpResponse<AiValidationResult> validateProposal( @Body final AiValidationRequest request ) {
      return HttpResponse.ok( modelValidator.validate( request.turtle(), null, null, null ) );
   }
}
