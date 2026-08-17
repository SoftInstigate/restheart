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

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;

/**
 * Response interceptor for order creation.
 *
 * <p>A {@code POST} to a MongoDB collection returns {@code 201} with a {@code Location}
 * header and no body. This interceptor puts {@code {_id, checkout_url, secret}} in the
 * response body so the client can redirect to Stripe without a second round trip.
 */
@RegisterPlugin(
        name = "ordersCheckoutResponseInterceptor",
        description = "Adds _id, checkout_url and secret to POST /orders response",
        interceptPoint = InterceptPoint.RESPONSE,
        requiresContent = true,
        enabledByDefault = false)
public class OrdersCheckoutResponseInterceptor implements MongoInterceptor {

    @Inject("stripeConfig")
    private StripeConfigData conf;

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var products = conf.products();
        if (products == null || !products.enabled()) {
            return;
        }

        // Only process successful POST (201 Created)
        if (response.getStatusCode() != HttpStatus.SC_CREATED) {
            return;
        }

        // Read the request content (the order document we built)
        if (!(request.getContent() instanceof BsonDocument order)) {
            return;
        }

        // Extract the relevant fields for the client
        var clientResponse = new BsonDocument();

        if (order.containsKey("_id")) {
            clientResponse.append("_id", order.get("_id"));
        }

        if (order.containsKey("checkout_url")) {
            clientResponse.append("checkout_url", order.get("checkout_url"));
        }

        if (order.containsKey("secret")) {
            clientResponse.append("secret", order.get("secret"));
        }

        // Set the response body
        response.setContent(clientResponse);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        var products = conf.products();
        if (products == null || !products.enabled()) {
            return false;
        }

        return request.isPost()
                && request.isWriteDocument()
                && products.ordersCollection().equals(request.getCollectionName());
    }
}
