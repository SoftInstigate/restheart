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
package org.restheart.stripe.products;

import java.util.List;

/**
 * A parsed product from the {@code catalog} MongoDB collection.
 *
 * <p>The catalog is the sole price authority — the client never sends a price.
 *
 * @param id             product id ({@code _id} in MongoDB)
 * @param type           {@code "physical"} or {@code "digital"}
 * @param name           display name
 * @param description    optional description
 * @param images         product images, most representative first — a variant's replace the
 *                       product's rather than adding to them, so choosing a colour shows that
 *                       colour. Never null; empty when there are none.
 * @param unitAmount     price in smallest currency unit (non-negative integer)
 * @param currency       currency code, or {@code null} to use the default
 * @param purchasable    whether this product can be purchased
 * @param taxCode        Stripe tax code, or {@code null}
 * @param stripePriceId  escape hatch: a real Stripe Price id, or {@code null} to use ad-hoc {@code price_data}
 */
public record CatalogItem(
        String id,
        String type,
        String name,
        String description,
        List<String> images,
        long unitAmount,
        String currency,
        boolean purchasable,
        String taxCode,
        String stripePriceId,
        /**
         * Units on hand, or {@code null} for unlimited.
         *
         * <p>Read from the same document as the price — from the variant when the reference names
         * one. A field that is simply absent means the shop does not count this item, which is the
         * right default: most of what a small shop sells it can always get more of.
         */
        Integer inStock) {

    /** Product types. */
    public static final String PHYSICAL = "physical";
    public static final String DIGITAL = "digital";

    public boolean isPhysical() {
        return PHYSICAL.equals(type);
    }
}
