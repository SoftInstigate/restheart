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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.restheart.emails.EmailSender;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.BillingScope;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.security.ACLRegistry;
import org.restheart.stripe.util.RequestOverrides;
import org.restheart.stripe.util.StripeCatalogCache;
import org.restheart.stripe.webhook.OrderEventHandler;
import org.restheart.stripe.webhook.StripeEventContext;
import org.restheart.stripe.webhook.StripeEventHandler;
import org.restheart.stripe.webhook.SubscriptionEventHandler;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

/**
 * {@code POST /stripe/webhook} — verifies the {@code Stripe-Signature} header against the
 * raw request body and dispatches billing events to registered {@link StripeEventHandler}s.
 *
 * <p>This is the module's only public endpoint. Security is provided exclusively by
 * signature verification against {@code stripeConfig.webhook-secret} (or its per-tenant
 * override) — there is no authenticated caller on this path.
 *
 * <p>⚠️ Implements {@link ByteArrayService}, not a JSON/BSON service: the signature is an
 * HMAC-SHA256 over the exact bytes Stripe sent, and any re-serialisation (key reordering,
 * whitespace, number formatting) invalidates it. Nothing is parsed until the signature is
 * verified.
 *
 * <h2>Status codes</h2>
 * <ul>
 *   <li>{@code 400} — signature verification failed, or no webhook secret is configured</li>
 *   <li>{@code 200} — verified event, whether handled, unhandled, or skipped as stale: Stripe
 *       must stop retrying an event that succeeded or that a newer update has superseded</li>
 *   <li>{@code 500} — an unexpected failure (e.g. MongoDB) applying an otherwise-valid event
 *       — Stripe should retry</li>
 * </ul>
 */
@RegisterPlugin(
        name = "stripeWebhookService",
        description = "POST /stripe/webhook — verify Stripe signature and process billing events",
        defaultURI = "/stripe/webhook",
        enabledByDefault = false)
public class StripeWebhookService implements ByteArrayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeWebhookService.class);

    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    @Inject("stripeConfig")
    private StripeConfigData conf;

    @Inject("stripeService")
    private StripeService stripeService;

    @Inject("stripeCatalogCache")
    private StripeCatalogCache catalogCache;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("registry")
    private PluginsRegistry registry;

    @Inject("mclient")
    private com.mongodb.client.MongoClient mclient;

    private EmailSender emailSender;
    private final List<StripeEventHandler> handlers = new ArrayList<>();

    @OnInit
    public void onInit() {
        aclRegistry.registerAllow(r -> "/stripe/webhook".equals(r.getPath()) && (r.isPost() || r.isOptions()));

        // Resolve email sender softly
        for (var providerRecord : registry.getProviders()) {
            if ("emails".equals(providerRecord.getName()) && providerRecord.isEnabled()) {
                Object value = providerRecord.getInstance().get(null);
                if (value instanceof EmailSender sender) {
                    this.emailSender = sender;
                }
                break;
            }
        }
        if (emailSender == null) {
            LOGGER.info("[stripe] 'emails' plugin not found or not enabled — billing notifications will not be sent");
        }

        // Register subscription event handler
        handlers.add(new SubscriptionEventHandler(catalogCache, emailSender));

        // Register order event handler (products mode)
        handlers.add(new OrderEventHandler(emailSender));
    }

    @Override
    public void handle(ByteArrayRequest req, ByteArrayResponse res) throws Exception {
        if (req.isOptions()) {
            handleOptions(req);
            return;
        }
        if (!req.isPost()) {
            res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var rawBody = req.getContentString();
        var sigHeader = req.getHeader(STRIPE_SIGNATURE_HEADER);
        var webhookSecret = RequestOverrides.webhookSecret(req, conf);

        if (webhookSecret == null || webhookSecret.isBlank()) {
            LOGGER.error("[stripe] webhook received but no webhook-secret is configured — rejecting");
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }

        if (sigHeader == null || sigHeader.isBlank()) {
            LOGGER.warn("[stripe] webhook received without a Stripe-Signature header — rejecting");
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }

        Event event;
        try {
            event = Webhook.constructEvent(rawBody, sigHeader, webhookSecret);
        } catch (SignatureVerificationException | IllegalArgumentException e) {
            LOGGER.warn("[stripe] webhook signature verification failed: {}", e.getMessage());
            res.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }

        var ctx = new StripeEventContext(
                req,
                conf,
                stripeService.getSubscriptionOwnerProvider(),
                RequestOverrides.scope(req, conf),
                RequestOverrides.defaultPlan(req, conf),
                Instant.ofEpochSecond(event.getCreated() != null ? event.getCreated() : Instant.now().getEpochSecond()),
                mclient);

        try {
            var handled = false;
            for (var handler : handlers) {
                if (handler.handledEventTypes().contains(event.getType())) {
                    handler.handle(event, ctx);
                    handled = true;
                }
            }

            if (!handled) {
                LOGGER.debug("[stripe] unhandled event type: {}", event.getType());
            }
        } catch (RuntimeException e) {
            LOGGER.error("[stripe] webhook handler for {} failed unexpectedly", event.getType(), e);
            res.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        res.setStatusCode(HttpStatus.SC_OK);
    }
}
