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
package org.eclipse.esmf.ame.ai.provider;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;

/**
 * An AI provider for tests with configurable kinds, availability and model list.
 */
final class StubAiProvider implements AiProvider {
   private final Set<AiProviderKind> kinds;
   private final boolean available;
   private final Function<AiProviderSettings, List<AiModelInfo>> models;
   private final List<AiProviderSettings> requests = new CopyOnWriteArrayList<>();

   StubAiProvider( final Set<AiProviderKind> kinds, final boolean available,
         final Function<AiProviderSettings, List<AiModelInfo>> models ) {
      this.kinds = EnumSet.copyOf( kinds );
      this.available = available;
      this.models = models;
   }

   StubAiProvider( final AiProviderKind kind, final AiProviderKind... more ) {
      this( EnumSet.of( kind, more ), true, settings -> List.of( AiModelInfo.of( "model-1", "Model 1" ) ) );
   }

   List<AiProviderSettings> requests() {
      return requests;
   }

   @Override
   public Set<AiProviderKind> kinds() {
      return kinds;
   }

   @Override
   public boolean isAvailable() {
      return available;
   }

   @Override
   public List<AiModelInfo> listModels( final AiProviderSettings settings ) {
      requests.add( settings );
      return models.apply( settings );
   }

   @Override
   public AiChatResult chat( final AiProviderSettings settings, final AiChatTurn turn, final AiChatListener listener ) {
      throw new UnsupportedOperationException( "chat is not scripted" );
   }
}
