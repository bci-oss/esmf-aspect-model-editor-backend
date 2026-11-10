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
package org.eclipse.esmf.ame.ai.chat;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.esmf.ame.ai.model.provider.AiModelInfo;
import org.eclipse.esmf.ame.ai.provider.AiChatListener;
import org.eclipse.esmf.ame.ai.provider.AiChatResult;
import org.eclipse.esmf.ame.ai.provider.AiChatTurn;
import org.eclipse.esmf.ame.ai.provider.AiProvider;
import org.eclipse.esmf.ame.ai.provider.AiProviderKind;
import org.eclipse.esmf.ame.ai.provider.AiProviderSettings;

/**
 * An AI provider for tests that answers with a script.
 */
public class FakeAiProvider implements AiProvider {
   @FunctionalInterface
   public interface Script {
      AiChatResult answer( AiProviderSettings settings, AiChatTurn turn, AiChatListener listener ) throws Exception;
   }

   private volatile Script script = ( settings, turn, listener ) -> {
      listener.onDelta( "Hello" );
      return new AiChatResult( "Hello", 1L, 1L );
   };
   private final List<AiChatTurn> turns = new CopyOnWriteArrayList<>();
   private final List<AiProviderSettings> settings = new CopyOnWriteArrayList<>();

   public void setScript( final Script script ) {
      this.script = script;
   }

   public List<AiChatTurn> turns() {
      return turns;
   }

   public List<AiProviderSettings> settings() {
      return settings;
   }

   @Override
   public Set<AiProviderKind> kinds() {
      return EnumSet.allOf( AiProviderKind.class );
   }

   @Override
   public List<AiModelInfo> listModels( final AiProviderSettings providerSettings ) {
      settings.add( providerSettings );
      return List.of( new AiModelInfo( "fake-model", "Fake model" ) );
   }

   @Override
   public AiChatResult chat( final AiProviderSettings providerSettings, final AiChatTurn turn, final AiChatListener listener ) {
      settings.add( providerSettings );
      turns.add( turn );
      try {
         return script.answer( providerSettings, turn, listener );
      } catch ( final RuntimeException exception ) {
         throw exception;
      } catch ( final Exception exception ) {
         throw new IllegalStateException( exception );
      }
   }
}
