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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

/**
 * Takes stock off the shelf when an order is paid.
 *
 * <p><b>Nothing is reserved.</b> A cart holds no claim on anything: the units come off at payment,
 * in one atomic update per line, and if two buyers take the last one both payments succeed. That
 * is a deliberate trade. Reserving would mean an endpoint, a server-issued token, a per-cart cap,
 * a rate limit, an array growing inside the product document, expiry arithmetic, and a cart that
 * stops being client-side — more moving parts than everything else in this feature put together,
 * to prevent something that happens when two people want the last unit within the same half hour.
 * Overselling costs a fee and an apologetic email, and is paid rarely; the complexity would be
 * paid always.
 *
 * <p>What the atomic decrement buys is not prevention but <b>detection</b>: {@code $inc} returns
 * the number it produced, so a line that pushes the count below zero says so, the order is marked
 * {@code oversold}, and a human refunds it from Stripe. Nobody refunds what nobody noticed.
 */
public final class StockKeeper {
    private static final Logger LOGGER = LoggerFactory.getLogger(StockKeeper.class);

    private StockKeeper() {
    }

    /**
     * One line that took more than there was.
     *
     * @param productId the reference as ordered, variant included
     * @param quantity  units the order took
     * @param remaining what the count reads now — negative, and by how much says how many were
     *                  sold that do not exist
     */
    public record Oversold(String productId, int quantity, int remaining) {
    }

    /**
     * Removes the ordered quantities, and reports the lines that went below zero.
     *
     * <p>Call once per order, behind whatever guard makes that true — Stripe redelivers a webhook
     * freely, and a decrement applied twice invents a shortage.
     *
     * @param db               the tenant database
     * @param catalogCollection collection holding the products
     * @param quantities       units to remove, keyed by the reference as ordered
     *                         ({@code tee-classic} or {@code tee-classic/yellow-l})
     * @return the lines that oversold, empty when every line had the stock for it
     */
    public static List<Oversold> take(MongoDatabase db, String catalogCollection,
                                      Map<String, Integer> quantities) {
        var catalog = db.getCollection(catalogCollection, BsonDocument.class);
        var oversold = new ArrayList<Oversold>();

        for (var line : quantities.entrySet()) {
            var requested = line.getKey();
            var quantity = line.getValue();
            if (quantity == null || quantity <= 0) {
                continue;
            }

            var documentId = CatalogReader.documentIdOf(requested);
            var variantId = CatalogReader.variantIdOf(requested);

            // `in_stock` must exist in the filter, not just be read afterwards: absent means the
            // shop does not count this item, and $inc would helpfully create the field at -3.
            var filter = variantId == null
                    ? Filters.and(Filters.eq("_id", documentId), Filters.exists("in_stock"))
                    : Filters.and(Filters.eq("_id", documentId),
                            Filters.elemMatch("variants",
                                    Filters.and(Filters.eq("id", variantId), Filters.exists("in_stock"))));

            var field = variantId == null ? "in_stock" : "variants.$.in_stock";

            var after = catalog.findOneAndUpdate(filter, Updates.inc(field, -quantity),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));

            if (after == null) {
                // Uncounted, or gone from the catalog between the checkout and the payment. Either
                // way there is no number to keep honest.
                LOGGER.debug("[stripe] stock not tracked for {} — nothing to take", requested);
                continue;
            }

            var remaining = remainingOf(after, variantId);
            if (remaining == null) {
                continue;
            }

            LOGGER.info("[stripe] took {} of {} — {} left", quantity, requested, remaining);

            if (remaining < 0) {
                oversold.add(new Oversold(requested, quantity, remaining));
            }
        }

        return oversold;
    }

    /** Units to remove per reference, summed over an order's lines. */
    public static Map<String, Integer> quantitiesOf(BsonDocument order) {
        var quantities = new LinkedHashMap<String, Integer>();
        if (!order.containsKey("line_items") || !order.get("line_items").isArray()) {
            return quantities;
        }

        for (var element : order.getArray("line_items")) {
            if (!element.isDocument()) {
                continue;
            }
            var line = element.asDocument();
            var productId = line.containsKey("product_id") && line.get("product_id").isString()
                    ? line.getString("product_id").getValue()
                    : null;
            var quantity = intOf(line.get("quantity"));
            if (productId == null || quantity == null) {
                continue;
            }
            // A cart may name the same reference on two lines; the shelf sees the sum.
            quantities.merge(productId, quantity, Integer::sum);
        }

        return quantities;
    }

    /** What the count reads after the update, or {@code null} if it cannot be read as a number. */
    static Integer remainingOf(BsonDocument after, String variantId) {
        if (variantId == null) {
            return intOf(after.get("in_stock"));
        }

        if (!after.containsKey("variants") || !after.get("variants").isArray()) {
            return null;
        }

        for (var element : after.getArray("variants")) {
            if (!element.isDocument()) {
                continue;
            }
            var variant = element.asDocument();
            if (variant.containsKey("id") && variant.get("id").isString()
                    && variantId.equals(variant.getString("id").getValue())) {
                return intOf(variant.get("in_stock"));
            }
        }

        return null;
    }

    private static Integer intOf(BsonValue value) {
        if (value == null) {
            return null;
        }
        if (value.isInt32()) {
            return value.asInt32().getValue();
        }
        if (value.isInt64()) {
            return (int) value.asInt64().getValue();
        }
        return null;
    }
}
