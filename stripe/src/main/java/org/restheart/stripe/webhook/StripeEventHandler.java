/*-
 * ========================LICENSE_START=================================
 * restheart-stripe
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
package org.restheart.stripe.webhook;

import java.util.Set;

import com.stripe.model.Event;

/**
 * SPI for Stripe webhook event handlers.
 *
 * <p>Implementations handle specific event types. The {@code StripeWebhookService}
 * dispatches verified events to all registered handlers whose
 * {@link #handledEventTypes()} contains the event type.
 *
 * <p>This is an internal structuring device, not a module boundary.
 */
public interface StripeEventHandler {

    /**
     * @return the set of Stripe event types this handler processes
     */
    Set<String> handledEventTypes();

    /**
     * Handles a verified Stripe event.
     *
     * @param event the verified Stripe event
     * @param ctx   per-delivery context
     * @throws Exception if handling fails (the caller decides the HTTP status)
     */
    void handle(Event event, StripeEventContext ctx) throws Exception;
}
