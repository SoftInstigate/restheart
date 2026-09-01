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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;

/**
 * Deserialization of the {@code data.object} payload of a Stripe event.
 */
final class EventPayloads {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventPayloads.class);

    private EventPayloads() {
    }

    /**
     * Deserializes the event payload as {@code type}.
     *
     * <p>{@code EventDataObjectDeserializer.getObject()} returns an empty {@code Optional}
     * without raising anything when the event's {@code api_version} differs from the SDK's
     * {@link Stripe#API_VERSION}. That mismatch is the normal state of affairs, not an edge
     * case: the API version is pinned on the Stripe account and drifts from the bundled SDK
     * whenever either side is upgraded. This falls back to {@code deserializeUnsafe()}, which
     * skips the version check, as recommended by Stripe.
     *
     * @param event the verified Stripe event
     * @param type  the expected payload type
     * @return the deserialized payload, or {@code null} if it could not be deserialized
     *         or is not of the expected type
     */
    @SuppressWarnings("unchecked")
    static <T extends StripeObject> T deserialize(Event event, Class<T> type) {
        var deserializer = event.getDataObjectDeserializer();

        var payload = deserializer.getObject().orElse(null);

        if (payload == null) {
            LOGGER.debug("[stripe] event {} has api_version {} but the SDK is pinned to {}; retrying with deserializeUnsafe()",
                    event.getType(), event.getApiVersion(), Stripe.API_VERSION);
            try {
                payload = deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                LOGGER.error("[stripe] could not deserialize {} payload as {} (event api_version {}, SDK api_version {})",
                        event.getType(), type.getSimpleName(), event.getApiVersion(), Stripe.API_VERSION, e);
                return null;
            }
        }

        if (!type.isInstance(payload)) {
            LOGGER.error("[stripe] {} payload is a {}, expected a {}",
                    event.getType(), payload.getClass().getSimpleName(), type.getSimpleName());
            return null;
        }

        return (T) payload;
    }
}
