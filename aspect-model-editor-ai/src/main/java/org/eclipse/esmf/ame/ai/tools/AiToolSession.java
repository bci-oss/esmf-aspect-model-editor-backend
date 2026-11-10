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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.eclipse.esmf.ame.ai.model.chat.AiChatContext;
import org.eclipse.esmf.ame.ai.model.proposal.AiProposal;

/**
 * The state of the tools during one chat request: the context sent by the editor and the proposals made so far.
 */
public final class AiToolSession {
   private final AiChatContext context;
   private final Consumer<AiProposal> proposalSink;
   private final List<AiProposal> proposals = new ArrayList<>();
   private final AtomicInteger invalidModelAttempts = new AtomicInteger();

   /**
    * @param context the context sent by the editor; {@code null} for none
    * @param proposalSink receives each proposal as soon as it is made
    */
   public AiToolSession( final AiChatContext context, final Consumer<AiProposal> proposalSink ) {
      this.context = context == null ? AiChatContext.EMPTY : context;
      this.proposalSink = proposalSink;
   }

   /**
    * @return the context sent by the editor
    */
   public AiChatContext context() {
      return context;
   }

   /**
    * @return the proposals made so far
    */
   public synchronized List<AiProposal> proposals() {
      return List.copyOf( proposals );
   }

   void propose( final AiProposal proposal ) {
      synchronized ( this ) {
         proposals.add( proposal );
      }
      proposalSink.accept( proposal );
   }

   /**
    * Counts an invalid model proposal.
    *
    * @return the number of invalid proposals so far
    */
   int countInvalidModel() {
      return invalidModelAttempts.incrementAndGet();
   }

   void resetInvalidModels() {
      invalidModelAttempts.set( 0 );
   }
}
