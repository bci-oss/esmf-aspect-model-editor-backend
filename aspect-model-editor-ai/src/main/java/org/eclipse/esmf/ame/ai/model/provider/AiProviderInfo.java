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
 * Describes a supported AI provider.
 *
 * @param kind the id of the provider
 * @param label the display name
 * @param requiresKey whether an API key is needed
 * @param requiresBaseUrl whether a base URL must be configured
 * @param defaultBaseUrl the base URL used if none is configured
 * @param local whether the provider runs on the local machine
 * @param available whether the provider can be used, e.g. the Copilot runtime is bundled
 * @param defaultModel the model used if none is configured
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiProviderInfo(
      String kind,
      String label,
      boolean requiresKey,
      boolean requiresBaseUrl,
      @Nullable String defaultBaseUrl,
      boolean local,
      boolean available,
      @Nullable String defaultModel
) {
}
