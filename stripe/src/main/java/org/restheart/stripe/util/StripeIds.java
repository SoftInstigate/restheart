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
package org.restheart.stripe.util;

import org.bson.BsonValue;

/** Small helpers for converting between {@link BsonValue} entity ids and strings. */
public final class StripeIds {

    private StripeIds() {
    }

    /**
     * @param id an entity id, typically {@link SubscriptionOwner#id()}
     * @return the hex string for an ObjectId, the raw value for a string id, or
     *         {@link Object#toString()} for anything else; {@code null} for {@code null}
     */
    public static String toIdString(BsonValue id) {
        if (id == null) {
            return null;
        }
        if (id.isObjectId()) {
            return id.asObjectId().getValue().toHexString();
        }
        if (id.isString()) {
            return id.asString().getValue();
        }
        return id.toString();
    }
}
