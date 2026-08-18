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
import org.bson.BsonString;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.BillingScope;
import org.restheart.plugins.stripe.ProductsConfig;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.stripe.products.OrderSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;

/**
 * Injectable service for on-demand, per-database initialization of both modules of
 * {@code restheart-stripe}: subscriptions and products.
 *
 * <p>{@code stripeInitializer} calls {@link #initSubscriptions()} / {@link #initProducts()} (no
 * argument — the statically configured database) at startup, so a single-tenant deployment's
 * behavior is unchanged. A multi-tenant deployment such as RESTHeart Cloud instead calls
 * {@link #initSubscriptions(String)} / {@link #initProducts(String)} itself, once per tenant
 * database, when that tenant enables the corresponding mode — there is no per-tenant startup
 * hook to piggy-back on, since one process serves many tenants.
 *
 * <p>Every operation is idempotent (create-if-absent for collections, indexes and schema), so
 * calling either method again — because a tenant re-enabled the plugin, or because a deployment
 * chooses to call it unconditionally at provisioning time — is safe.
 *
 * <p>Usage:
 * <pre>{@code
 * @Inject("stripeInitService")
 * private StripeInitService initService;
 *
 * // When a service enables subscriptions or products:
 * initService.initSubscriptions("tenant-db-name");
 * initService.initProducts("tenant-db-name");
 * }</pre>
 */
@RegisterPlugin(
        name = "stripeInitService",
        description = "On-demand, per-database initialization of subscriptions and products mode",
        enabledByDefault = false)
public class StripeInitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeInitService.class);

    @Inject("stripeConfig")
    private StripeConfigData conf;

    @Inject("stripeService")
    private StripeService stripeService;

    @Inject("stripeInitTracker")
    private StripeInitTracker initTracker;

    @Inject("mclient")
    private MongoClient mclient;

    // ── Subscriptions ───────────────────────────────────────────────────────

    /**
     * Ensures the {@code stripe_customer_id} unique index on the statically configured database's
     * teams collection.
     *
     * @throws IllegalArgumentException if subscriptions mode is not enabled
     */
    public void initSubscriptions() {
        initSubscriptions(conf.db());
    }

    /**
     * Ensures the {@code stripe_customer_id} unique index on {@code dbName}'s teams collection —
     * the one index every webhook delivery depends on (see {@code TeamRepository.ensureIndexes}).
     * Without it, {@code byStripeCustomerId} does a collection scan on every delivery, and nothing
     * prevents two tenants from being linked to the same Stripe Customer.
     *
     * <p>A no-op when the active {@link org.restheart.plugins.stripe.SubscriptionOwnerProvider} is
     * not the default one — a custom provider owns its own storage and indexing.
     *
     * @throws IllegalArgumentException if subscriptions mode is not enabled
     */
    public void initSubscriptions(String dbName) {
        if (conf.subscriptions() == null || !conf.subscriptions().enabled()) {
            throw new IllegalArgumentException("[stripe] subscriptions mode is not enabled");
        }

        var defaultProvider = stripeService.defaultProviderOrNull();
        if (defaultProvider == null) {
            LOGGER.debug("[stripe] active SubscriptionOwnerProvider is not the default one — "
                    + "skipping index creation for '{}'", dbName);
            return;
        }

        LOGGER.info("[stripe] initializing subscriptions mode on database '{}'", dbName);
        defaultProvider.repository().ensureIndexes(new BillingScope(dbName, conf.teamsCollection()));
        initTracker.markSubscriptionsInitialized(dbName);
        LOGGER.info("[stripe] subscriptions mode initialized on database '{}'", dbName);
    }

    // ── Products ─────────────────────────────────────────────────────────────

    /**
     * Initializes the products mode domain model on the statically configured database.
     *
     * @throws IllegalArgumentException if products mode is not configured
     */
    public void initProducts() {
        if (conf.products() == null) {
            throw new IllegalArgumentException("[stripe] products mode is not configured");
        }

        initProducts(conf.db(), conf.products());
    }

    /**
     * Initializes the products mode domain model on the given database: collections, indexes,
     * and the order JSON schema — the same state {@code stripeInitializer} installs at startup
     * for a single-tenant deployment.
     *
     * @param dbName the database name to initialize
     * @throws IllegalArgumentException if products mode is not configured
     */
    public void initProducts(String dbName) {
        if (conf.products() == null) {
            throw new IllegalArgumentException("[stripe] products mode is not configured");
        }

        initProducts(dbName, conf.products());
    }

    private void initProducts(String dbName, ProductsConfig products) {
        var db = mclient.getDatabase(dbName);

        LOGGER.info("[stripe] initializing products mode on database '{}'", dbName);

        createCollectionIfAbsent(db, products.catalogCollection());
        createCollectionIfAbsent(db, products.ordersCollection());
        createCollectionIfAbsent(db, products.transactionsCollection());
        if (products.inventoryCollection() != null) {
            createCollectionIfAbsent(db, products.inventoryCollection());
        }

        createOrdersIndexes(db, products);
        createTransactionsIndexes(db, products);
        installOrderSchema(db, products);

        initTracker.markProductsInitialized(dbName);
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

    private void createIndexIfAbsent(MongoCollection<BsonDocument> col, org.bson.conversions.Bson keys,
                                      IndexOptions options, String collectionName, String dbName) {
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

    /**
     * Installs the order JSON schema into the {@code _schemas} collection and sets the
     * {@code jsonSchema} metadata on the orders collection. Idempotent: create-if-absent for the
     * schema document, upsert for the collection metadata.
     */
    private void installOrderSchema(MongoDatabase db, ProductsConfig products) {
        var schemasCol = db.getCollection("_schemas", BsonDocument.class);

        var existing = schemasCol.find(Filters.eq("_id", OrderSchema.SCHEMA_ID)).first();
        if (existing == null) {
            try {
                var schemaDoc = OrderSchema.schema();
                schemaDoc.append("_id", new BsonString(OrderSchema.SCHEMA_ID));
                schemasCol.insertOne(schemaDoc);
                LOGGER.info("[stripe] installed schema '{}' into _schemas on '{}'", OrderSchema.SCHEMA_ID, db.getName());
            } catch (MongoException e) {
                if (e.getCode() == 11000) {
                    LOGGER.debug("[stripe] schema '{}' already exists on '{}'", OrderSchema.SCHEMA_ID, db.getName());
                } else {
                    LOGGER.error("[stripe] failed to install schema '{}' on '{}': {}",
                            OrderSchema.SCHEMA_ID, db.getName(), e.getMessage());
                }
            }
        } else {
            LOGGER.debug("[stripe] schema '{}' already exists on '{}'", OrderSchema.SCHEMA_ID, db.getName());
        }

        var propsCol = db.getCollection("_properties", BsonDocument.class);
        var propsId = "_properties." + products.ordersCollection();
        var propsDoc = new BsonDocument()
                .append("_id", new BsonString(propsId))
                .append("jsonSchema", new BsonDocument()
                        .append("schemaId", new BsonString(OrderSchema.SCHEMA_ID)));

        try {
            propsCol.replaceOne(Filters.eq("_id", propsId), propsDoc, new ReplaceOptions().upsert(true));
            LOGGER.info("[stripe] set jsonSchema metadata on '{}.{}'", db.getName(), products.ordersCollection());
        } catch (MongoException e) {
            LOGGER.error("[stripe] failed to set jsonSchema metadata on '{}.{}': {}",
                    db.getName(), products.ordersCollection(), e.getMessage());
        }
    }
}
