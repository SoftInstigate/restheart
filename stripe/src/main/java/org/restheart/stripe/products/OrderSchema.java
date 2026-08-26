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

/**
 * JSON schema for the order document ({@code stripe-order-v1}).
 *
 * <p>This schema validates the <em>final</em> order document — the one the interceptor
 * builds, not the client body. It is installed by {@code StripeInitializer} into the
 * {@code _schemas} collection.
 *
 * <p>The schema is deliberately permissive on the client-facing side (the interceptor
 * validates input strictly) and strict on the structural side (the persisted document
 * must be well-formed).
 */
public final class OrderSchema {

    /** Schema id used in {@code _schemas} and in collection metadata. */
    public static final String SCHEMA_ID = "stripe-order-v1";

    private OrderSchema() {}

    /**
     * Returns the JSON schema as a BsonDocument.
     */
    public static BsonDocument schema() {
        return BsonDocument.parse(SCHEMA_JSON);
    }

    private static final String SCHEMA_JSON = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "title": "Stripe Order",
              "description": "Order document created by the products mode interceptor",
              "type": "object",
              "required": [
                "_id",
                "stripe_session_id",
                "secret",
                "checkout_url",
                "status",
                "line_items",
                "currency",
                "amount_subtotal",
                "amount_total",
                "amount_refunded",
                "created_at",
                "expires_at"
              ],
              "properties": {
                "_id": { "type": "object" },
                "stripe_session_id": { "type": "string" },
                "stripe_payment_intent": { "type": ["string", "null"] },
                "secret": { "type": "string" },
                "checkout_url": { "type": "string" },
                "buyer_id": { "type": ["string", "null"] },
                "buyer_email": { "type": ["string", "null"] },
                "payer": {
                  "type": "object",
                  "required": ["type"],
                  "properties": {
                    "type": { "type": "string", "enum": ["team", "guest"] },
                    "id": { "type": ["object", "null"] },
                    "stripe_customer_id": { "type": ["string", "null"] }
                  }
                },
                "status": {
                  "type": "string",
                  "enum": ["pending_payment", "paid", "failed", "expired"]
                },
                "requires_shipping": { "type": "boolean" },
                "line_items": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "required": ["product_id", "type", "name", "unit_amount", "quantity", "subtotal"],
                    "properties": {
                      "product_id": { "type": "string" },
                      "type": { "type": "string", "enum": ["physical", "digital"] },
                      "name": { "type": "string" },
                      "unit_amount": { "type": "integer", "minimum": 0 },
                      "quantity": { "type": "integer", "minimum": 1 },
                      "subtotal": { "type": "integer", "minimum": 0 },
                      "tax_code": { "type": ["string", "null"] },
                      "metadata": {
                        "type": "object",
                        "maxProperties": 50,
                        "propertyNames": { "maxLength": 40 },
                        "additionalProperties": { "type": "string", "maxLength": 500 }
                      }
                    }
                  }
                },
                "currency": { "type": "string" },
                "amount_subtotal": { "type": "integer", "minimum": 0 },
                "amount_tax": { "type": "integer", "minimum": 0 },
                "amount_shipping": { "type": "integer", "minimum": 0 },
                "amount_total": { "type": "integer", "minimum": 0 },
                "amount_refunded": { "type": "integer", "minimum": 0 },
                "oversold": { "type": "boolean" },
                "shipping_address": {
                  "type": ["object", "null"],
                  "properties": {
                    "name": { "type": "string" },
                    "line1": { "type": "string" },
                    "line2": { "type": "string" },
                    "city": { "type": "string" },
                    "state": { "type": "string" },
                    "postal_code": { "type": "string" },
                    "country": { "type": "string" }
                  }
                },
                "metadata": {
                  "type": "object",
                  "maxProperties": 50,
                  "propertyNames": { "maxLength": 40 },
                  "additionalProperties": { "type": "string", "maxLength": 500 }
                },
                "created_at": { "type": "object" },
                "paid_at": { "type": ["object", "null"] },
                "expires_at": { "type": "object" }
              }
            }
            """;
}
