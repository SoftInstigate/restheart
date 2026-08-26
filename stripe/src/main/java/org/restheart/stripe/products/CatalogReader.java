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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.conversions.Bson;
import org.restheart.plugins.stripe.ProductsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;

/**
 * Reads and validates products from the {@code catalog} MongoDB collection.
 *
 * <p>The catalog is the sole price authority — the client never sends a price.
 * This reader validates every field and rejects invalid products with clear error messages.
 */
public class CatalogReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogReader.class);

    private final MongoClient mclient;
    private final String db;
    private final ProductsConfig config;

    public CatalogReader(MongoClient mclient, String db, ProductsConfig config) {
        this.mclient = mclient;
        this.db = db;
        this.config = config;
    }

    /**
     * Reads catalog items for the given product ids. Returns only valid, purchasable items.
     *
     * @param productIds the product ids to look up
     * @return valid catalog items
     * @throws CatalogValidationException if any item fails validation
     */
    public List<CatalogItem> readItems(Set<String> productIds) throws CatalogValidationException {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        // One query, whatever mix of plain products and variants was asked for: everything before
        // the slash is a document id.
        var documentIds = new java.util.LinkedHashSet<String>();
        for (var requested : productIds) {
            documentIds.add(documentIdOf(requested));
        }

        var byId = new java.util.HashMap<String, BsonDocument>();
        for (var doc : find(Filters.in("_id", documentIds))) {
            var docId = docString(doc, "_id");
            if (docId != null) {
                byId.put(docId, doc);
            }
        }

        var items = new ArrayList<CatalogItem>();
        for (var requested : productIds) {
            var doc = byId.get(documentIdOf(requested));
            if (doc == null) {
                // Missing products are the caller's to report — it knows which of them the buyer
                // asked for and can name them all at once.
                continue;
            }
            items.add(validate(resolve(doc, requested)));
        }

        return items;
    }

    /** Everything before the first slash: {@code tee-classic/yellow-l} is document {@code tee-classic}. */
    static String documentIdOf(String requested) {
        var slash = requested.indexOf('/');
        return slash < 0 ? requested : requested.substring(0, slash);
    }

    /** Everything after it, or {@code null} when a plain product was asked for. */
    private static String variantIdOf(String requested) {
        var slash = requested.indexOf('/');
        return slash < 0 ? null : requested.substring(slash + 1);
    }

    /**
     * The document to validate for a requested id: the product itself, or a variant flattened
     * onto it.
     *
     * <p>Flattened rather than read field by field, so that <em>every</em> field a variant
     * declares overrides the product's — price, name, tax code, images, whatever gets added next
     * year — and {@link #validate} stays the single description of what a purchasable item needs.
     * A variant that declares nothing but a price inherits the rest, which is the common case and
     * the reason a shop writes variants instead of whole products.
     *
     * <p>The resulting {@code _id} is the composite, so an order line records what was actually
     * bought rather than the family it belongs to.
     */
    static BsonDocument resolve(BsonDocument product, String requested)
            throws CatalogValidationException {
        var variantId = variantIdOf(requested);

        if (variantId == null) {
            if (product.get("variants") instanceof BsonArray variants && !variants.isEmpty()) {
                throw new CatalogValidationException(
                        ("product %s has variants and cannot be bought on its own — "
                                + "ask for one of them, as '%s/<variant>'")
                                .formatted(requested, requested));
            }
            return product;
        }

        if (!(product.get("variants") instanceof BsonArray variants)) {
            throw new CatalogValidationException(
                    "product %s has no variants".formatted(documentIdOf(requested)));
        }

        for (var value : variants) {
            if (value instanceof BsonDocument variant && variantId.equals(docString(variant, "id"))) {
                var merged = new BsonDocument();
                product.forEach((k, v) -> {
                    if (!"variants".equals(k)) {
                        merged.append(k, v);
                    }
                });
                variant.forEach((k, v) -> {
                    if (!"id".equals(k)) {
                        merged.append(k, v);
                    }
                });
                merged.put("_id", new BsonString(requested));
                return merged;
            }
        }

        throw new CatalogValidationException(
                "product %s has no variant '%s'".formatted(documentIdOf(requested), variantId));
    }

    /**
     * Checks that all items in the cart use the same currency.
     * Items without an explicit currency use the configured default.
     *
     * @param items catalog items in the cart
     * @throws CatalogValidationException if mixed currencies are detected
     */
    public void checkSingleCurrency(List<CatalogItem> items) throws CatalogValidationException {
        if (items.size() <= 1) {
            return;
        }

        String firstCurrency = null;
        for (var item : items) {
            var currency = item.currency() != null ? item.currency().toLowerCase() : config.defaultCurrency().toLowerCase();
            if (firstCurrency == null) {
                firstCurrency = currency;
            } else if (!firstCurrency.equals(currency)) {
                throw new CatalogValidationException(
                        "mixed currencies in cart: '%s' and '%s' — a checkout session has exactly one currency"
                                .formatted(firstCurrency, currency));
            }
        }
    }

    /**
     * Checks that all items are purchasable.
     *
     * @param items catalog items to check
     * @throws CatalogValidationException if any item is not purchasable
     */
    public void checkPurchasable(List<CatalogItem> items) throws CatalogValidationException {
        for (var item : items) {
            if (!item.purchasable()) {
                throw new CatalogValidationException(
                        "product %s (%s) is not purchasable".formatted(item.id(), item.name()));
            }
        }
    }

    /**
     * Reads a single catalog item by id.
     *
     * @param productId the product id
     * @return the catalog item, or empty if not found
     * @throws CatalogValidationException if the item fails validation
     */
    public Optional<CatalogItem> readItem(String productId) throws CatalogValidationException {
        var doc = collection().find(Filters.eq("_id", documentIdOf(productId))).first();
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(validate(resolve(doc, productId)));
    }

    private List<BsonDocument> find(Bson filter) {
        try {
            return collection().find(filter).into(new ArrayList<>());
        } catch (MongoException e) {
            LOGGER.error("[stripe] failed to read catalog: {}", e.getMessage());
            throw e;
        }
    }

    private com.mongodb.client.MongoCollection<BsonDocument> collection() {
        return mclient.getDatabase(db)
                .getCollection(config.catalogCollection(), BsonDocument.class);
    }

    /**
     * Validates a catalog document. Rejects with clear error messages.
     */
    private CatalogItem validate(BsonDocument doc) throws CatalogValidationException {
        var id = docString(doc, "_id");
        if (id == null) {
            throw new CatalogValidationException("catalog item has no _id");
        }

        var type = docString(doc, "type");
        if (type == null || (!CatalogItem.PHYSICAL.equals(type) && !CatalogItem.DIGITAL.equals(type))) {
            throw new CatalogValidationException(
                    "product %s has invalid type '%s' — expected '%s' or '%s'"
                            .formatted(id, type, CatalogItem.PHYSICAL, CatalogItem.DIGITAL));
        }

        var name = docString(doc, "name");
        if (name == null || name.isBlank()) {
            throw new CatalogValidationException("product %s has no name".formatted(id));
        }

        // unit_amount: must be a non-negative integer
        if (!doc.containsKey("unit_amount") || doc.get("unit_amount").isNull()) {
            throw new CatalogValidationException("product %s has no unit_amount".formatted(id));
        }

        long unitAmount;
        var unitAmountValue = doc.get("unit_amount");
        if (unitAmountValue.isInt32()) {
            unitAmount = unitAmountValue.asInt32().getValue();
        } else if (unitAmountValue.isInt64()) {
            unitAmount = unitAmountValue.asInt64().getValue();
        } else {
            throw new CatalogValidationException(
                    "product %s has a non-integer unit_amount (%s) — refusing to sell it"
                            .formatted(id, unitAmountValue));
        }

        if (unitAmount < 0) {
            throw new CatalogValidationException(
                    "product %s has a negative unit_amount (%d)".formatted(id, unitAmount));
        }

        // purchasable
        var purchasable = doc.getBoolean("purchasable", org.bson.BsonBoolean.TRUE).getValue();

        // optional fields
        var description = docString(doc, "description");
        var images = docStrings(doc, "images");
        var currency = docString(doc, "currency");
        var taxCode = docString(doc, "tax_code");
        var stripePriceId = docString(doc, "stripe_price_id");

        // reject recurring items (avoids collision with subscriptions mode)
        if (doc.containsKey("recurring") && !doc.get("recurring").isNull()
                && doc.getBoolean("recurring", org.bson.BsonBoolean.FALSE).getValue()) {
            throw new CatalogValidationException(
                    "product %s is marked as recurring — recurring products must use the subscriptions mode, not the products mode"
                            .formatted(id));
        }

        return new CatalogItem(id, type, name, description, images, unitAmount, currency, purchasable, taxCode, stripePriceId);
    }

    /**
     * A string array field, ignoring anything in it that is not a string.
     *
     * <p>Lenient on purpose: one malformed entry among a product's images should not stop the
     * product being sold. A missing price refuses the sale; a broken image URL is a picture that
     * does not load.
     */
    private static List<String> docStrings(BsonDocument doc, String key) {
        if (!(doc.get(key) instanceof BsonArray array)) {
            return List.of();
        }
        var out = new ArrayList<String>(array.size());
        for (var value : array) {
            if (value.isString() && !value.asString().getValue().isBlank()) {
                out.add(value.asString().getValue());
            }
        }
        return out;
    }

    private static String docString(BsonDocument doc, String key) {
        if (!doc.containsKey(key) || doc.get(key).isNull()) {
            return null;
        }
        if (doc.get(key).isString()) {
            return doc.get(key).asString().getValue();
        }
        return null;
    }

    /**
     * Exception thrown when a catalog item fails validation.
     */
    public static class CatalogValidationException extends Exception {
        public CatalogValidationException(String message) {
            super(message);
        }
    }
}
