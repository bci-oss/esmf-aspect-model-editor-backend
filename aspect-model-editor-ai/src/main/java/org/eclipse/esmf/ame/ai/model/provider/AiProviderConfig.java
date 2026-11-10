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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Nullable;

/**
 * The AI provider to use for a request. The API key is never part of it; it is sent in the header
 * {@code X-Ame-Ai-Key}.
 *
 * @param kind the provider, e.g. {@code openai} (see {@link org.eclipse.esmf.ame.ai.provider.AiProviderKind})
 * @param model the model id; a default is used if missing
 * @param baseUrl the base URL of the API; required for {@code azure-openai} and {@code openai-compatible}
 * @param apiVersion the API version, {@code azure-openai} only
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiProviderConfig(
      String kind,
      @Nullable String model,
      @Nullable String baseUrl,
      @Nullable String apiVersion
) {
}
