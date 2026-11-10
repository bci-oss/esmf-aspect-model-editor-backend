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
package org.eclipse.esmf.ame.ai.model.provider;

import java.util.List;

import io.micronaut.serde.annotation.Serdeable;

/**
 * A local model server (Ollama or LM Studio) that answered on localhost.
 *
 * @param kind {@code ollama} or {@code lmstudio}
 * @param baseUrl the OpenAI-compatible base URL
 * @param models the ids of the installed models
 */
@Serdeable
public record AiLocalServer( String kind, String baseUrl, List<String> models ) {
   public AiLocalServer {
      models = models == null ? List.of() : List.copyOf( models );
   }
}
