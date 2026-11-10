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
package org.eclipse.esmf.ame.ai.model.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Something the user sent as context with a message, shown with the message.
 *
 * @param kind {@code file}, {@code model}, {@code namespace} or {@code source}
 * @param label the shown name
 * @param id the attachment id of a file
 */
@Serdeable
@JsonInclude( JsonInclude.Include.NON_NULL )
public record AiMessageContextRef( String kind, String label, @Nullable String id ) {
   public static final String FILE = "file";
   public static final String MODEL = "model";
   public static final String NAMESPACE = "namespace";
   public static final String SOURCE = "source";
}
