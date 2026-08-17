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

import java.util.ArrayList;

import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Inject;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.BillingScope;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.security.AclVarsRegistry;
import org.restheart.stripe.acl.SubscriptionVarResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Startup initializer for {@code restheart-stripe}.
 *
 * <p>Four duties:
 * <ol>
 *   <li>validate shared {@code stripeConfig} fields (secret-key, webhook-secret)</li>
 *   <li>create the {@code stripe_customer_id} index used by every webhook delivery</li>
 *   <li>register the {@code @subscription} ACL variable (only if subscriptions mode is enabled)</li>
 *   <li>warn if products mode is enabled but no ACL permission grants access to the orders collection</li>
 * </ol>
 *
 * <h2>⚠️ {@code BEFORE_STARTUP}, not {@code AFTER_STARTUP}</h2>
 * <p>{@code AFTER_STARTUP} initializers run concurrently with request processing — the HTTP
 * server is already accepting requests by the time they execute. That would leave a real
 * window where the {@code stripe_customer_id} index does not exist yet, or {@code @subscription}
 * is not yet a registered ACL variable, while requests are already being served. {@code BEFORE_STARTUP}
 * runs after dependency injection is complete but before the server starts listening, closing
 * that window.
 *
 * <h2>⚠️ Validation logs, it does not abort startup</h2>
 * <p>RESTHeart catches every exception thrown from an {@code Initializer.init()} method (both
 * init points) and logs it — it does not stop the server. So required-field validation here
 * cannot "fail startup" in the sense of preventing the process from coming up; what it can do,
 * and does, is put a clear, specifically-worded error in the startup log rather than letting
 * the first checkout fail with an opaque Stripe {@code 401}.
 */
@RegisterPlugin(
        name = "stripeInitializer",
        description = "Validates stripeConfig, creates indexes, and registers ACL variables",
        initPoint = InitPoint.BEFORE_STARTUP,
        enabledByDefault = false)
public class StripeInitializer implements Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeInitializer.class);

    @Inject("stripeConfig")
    private StripeConfigData conf;

    @Inject("stripeService")
    private StripeService stripeService;

    @Inject("acl-vars-registry")
    private AclVarsRegistry aclVarsRegistry;

    @Override
    public void init() {
        // 1. Validate shared credentials — this blocks everything
        if (!validateShared()) {
            return;
        }

        // 2. Shared: stripe_customer_id index (always, regardless of mode validation)
        var defaultProvider = stripeService.defaultProviderOrNull();
        if (defaultProvider != null) {
            defaultProvider.repository().ensureIndexes(new BillingScope(conf.db(), conf.teamsCollection()));
        }

        // 3. Subscriptions: validate plans and register @subscription ACL variable
        if (conf.subscriptions() != null && conf.subscriptions().enabled()) {
            if (!validateSubscriptions()) {
                LOGGER.error("[stripe] subscriptions mode is enabled but plan validation failed — "
                        + "@subscription ACL variable will NOT be registered");
            } else {
                aclVarsRegistry.register(new SubscriptionVarResolver(stripeService, conf));
            }
        }

        // 4. Products: warn if no ACL permission grants access to the orders collection
        if (conf.products() != null && conf.products().enabled()) {
            LOGGER.warn("[stripe] products mode is enabled but no ACL permission grants access to `/orders` — "
                    + "POST /orders will answer 403 until one is configured (see documentation)");
        }

        var mode = conf.isLiveMode() ? "LIVE" : "TEST";
        var subEnabled = conf.subscriptions() != null && conf.subscriptions().enabled();
        var prodEnabled = conf.products() != null && conf.products().enabled();
        LOGGER.info("[stripe] plugin initialised — mode={}, db={}, subscriptions={}, products={}",
                mode, conf.db(), subEnabled, prodEnabled);
    }

    /** @return {@code false} if shared credentials are missing */
    private boolean validateShared() {
        var missing = new ArrayList<String>();
        if (conf.secretKey() == null || conf.secretKey().isBlank()) {
            missing.add("secret-key");
        }
        if (conf.webhookSecret() == null || conf.webhookSecret().isBlank()) {
            missing.add("webhook-secret");
        }

        if (!missing.isEmpty()) {
            LOGGER.error("[stripe] stripeConfig is missing required field(s): {} — the module will not function "
                    + "correctly until they are set", String.join(", ", missing));
            return false;
        }

        return true;
    }

    /** @return {@code false} if subscriptions plan validation fails */
    private boolean validateSubscriptions() {
        var sub = conf.subscriptions();
        if (sub.defaultPlan() == null || !sub.plans().containsKey(sub.defaultPlan())) {
            LOGGER.error("[stripe] stripeConfig.subscriptions.default-plan '{}' is not a plan declared in plans {}",
                    sub.defaultPlan(), sub.plans().keySet());
            return false;
        }
        return true;
    }
}
