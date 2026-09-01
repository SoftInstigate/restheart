/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins.stripe;

/**
 * Seat configuration for a single plan.
 *
 * @param mode how the seat limit is determined
 * @param max  the seat cap for {@link SeatsMode#CAPPED}, or an optional upper bound
 *             for {@link SeatsMode#PER_SEAT}; {@code null} for {@link SeatsMode#UNLIMITED}
 *             or when {@code PER_SEAT} has no upper bound
 */
public record SeatsConfig(SeatsMode mode, Integer max) {
}
