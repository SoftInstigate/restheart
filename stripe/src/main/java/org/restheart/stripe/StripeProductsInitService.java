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
package org.restheart.stripe;

import java.util.concurrent.TimeUnit;

import org.bson.BsonDocument;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.ProductsConfig;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.plugins.stripe.SubscriptionsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

/**
 * Injectable service for on-demand initialization of the products mode domain model.
 *
 * <p>When {@code products.init-enabled} is {@code false}, the automatic
 * {@link StripeInitializer} skips products-mode setup. RESTHeart Cloud calls this
 * service programmatically when a service enables payments, passing the tenant
 * database name.
 *
 * <p>The service is idempotent: create-if-absent for collections and indexes,
 * same rules as the automatic initializer.
 *
 * <p>Usage:
 * <pre>{@code
 * @Inject("stripeProductsInitService")
 * private StripeProductsInitService initService;
 *
 * // When a service enables payments:
 * initService.init("tenant-db-name");
 * }</pre>
 */
@RegisterPlugin(
        name = "stripeProductsInitService",
        description = "On-demand initialization of the products mode domain model",
        enabledByDefault = false)
public class StripeProductsInitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeProductsInitService.class);

    @Inject("stripeConfig")
    private StripeConfigData conf;

    @Inject("mclient")
    private MongoClient mclient;

    /**
     * Initializes the products mode domain model on the given database.
     *
     * @param dbName the database name to initialize
     * @throws IllegalArgumentException if products mode is not configured
     */
    public void init(String dbName) {
        if (conf.products() == null) {
            throw new IllegalArgumentException("[stripe] products mode is not configured");
        }

        init(dbName, conf.products());
    }

    /**
     * Initializes the products mode domain model on the statically configured database.
     *
     * @throws IllegalArgumentException if products mode is not configured
     */
    public void init() {
        if (conf.products() == null) {
            throw new IllegalArgumentException("[stripe] products mode is not configured");
        }

        init(conf.db(), conf.products());
    }

    private void init(String dbName, ProductsConfig products) {
        var db = mclient.getDatabase(dbName);

        LOGGER.info("[stripe] initializing products mode on database '{}'", dbName);

        // Create collections
        createCollectionIfAbsent(db, products.catalogCollection());
        createCollectionIfAbsent(db, products.ordersCollection());
        createCollectionIfAbsent(db, products.transactionsCollection());
        if (products.inventoryCollection() != null) {
            createCollectionIfAbsent(db, products.inventoryCollection());
        }

        // Create orders indexes
        createOrdersIndexes(db, products);

        // Create transactions indexes
        createTransactionsIndexes(db, products);

        LOGGER.info("[stripe] products mode initialized on database '{}'", dbName);
    }

    private void createCollectionIfAbsent(MongoDatabase db, String collectionName) {
        try {
            db.createCollection(collectionName);
            LOGGER.info("[stripe] created collection '{}' on '{}'", collectionName, db.getName());
        } catch (MongoException e) {
            if (e.getCode() == 48) {
                LOGGER.debug("[stripe] collection '{}' already exists on '{}'", collectionName, db.getName());
            } else {
                LOGGER.error("[stripe] failed to create collection '{}' on '{}': {}",
                        collectionName, db.getName(), e.getMessage());
            }
        }
    }

    private void createOrdersIndexes(MongoDatabase db, ProductsConfig products) {
        var col = db.getCollection(products.ordersCollection(), BsonDocument.class);

        createIndexIfAbsent(col, Indexes.ascending("stripe_session_id"),
                new IndexOptions().unique(true).name("stripe_session_id_unique"),
                products.ordersCollection(), db.getName());

        createIndexIfAbsent(col, Indexes.ascending("secret"),
                new IndexOptions().unique(true).name("secret_unique"),
                products.ordersCollection(), db.getName());

        createIndexIfAbsent(col, Indexes.compoundIndex(Indexes.ascending("buyer_id"), Indexes.descending("created_at")),
                new IndexOptions().name("buyer_id_created_at"),
                products.ordersCollection(), db.getName());

        var partialFilter = Filters.eq("status", "pending_payment");
        createIndexIfAbsent(col, Indexes.ascending("expires_at"),
                new IndexOptions()
                        .expireAfter(0L, TimeUnit.SECONDS)
                        .partialFilterExpression(partialFilter)
                        .name("expires_at_ttl_pending"),
                products.ordersCollection(), db.getName());
    }

    private void createTransactionsIndexes(MongoDatabase db, ProductsConfig products) {
        var col = db.getCollection(products.transactionsCollection(), BsonDocument.class);

        createIndexIfAbsent(col, Indexes.ascending("stripe_event_id"),
                new IndexOptions().unique(true).name("stripe_event_id_unique"),
                products.transactionsCollection(), db.getName());

        createIndexIfAbsent(col, Indexes.ascending("order_id"),
                new IndexOptions().name("order_id"),
                products.transactionsCollection(), db.getName());
    }

    private void createIndexIfAbsent(com.mongodb.client.MongoCollection<BsonDocument> col,
                                      org.bson.conversions.Bson keys, IndexOptions options,
                                      String collectionName, String dbName) {
        try {
            col.createIndex(keys, options);
            LOGGER.info("[stripe] created index '{}' on '{}.{}'", options.getName(), dbName, collectionName);
        } catch (MongoException e) {
            if (e.getCode() == 85) {
                LOGGER.debug("[stripe] index '{}' already exists on '{}.{}'", options.getName(), dbName, collectionName);
            } else {
                LOGGER.error("[stripe] failed to create index '{}' on '{}.{}': {}",
                        options.getName(), dbName, collectionName, e.getMessage());
            }
        }
    }
}
