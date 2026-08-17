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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.restheart.configuration.Configuration;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.NotificationConfig;
import org.restheart.plugins.stripe.PlanConfig;
import org.restheart.plugins.stripe.ProductsConfig;
import org.restheart.plugins.stripe.SeatsConfig;
import org.restheart.plugins.stripe.SeatsMode;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.plugins.stripe.SubscriptionsConfig;

/**
 * RESTHeart {@link Provider} that reads the {@code stripeConfig} YAML block and exposes
 * a {@link StripeConfigData} instance to other plugins via DI.
 *
 * <p>{@code db} is sourced from {@code mongoRealmAuthenticator/users-db}, for the same
 * reason {@code accountsConfig} sources its own {@code db} from there — see
 * {@link StripeConfigData}'s class javadoc.
 *
 * <p>Supports two YAML formats:
 * <ul>
 *   <li><b>Flat (legacy):</b> subscriptions fields at the top level of {@code stripeConfig}</li>
 *   <li><b>Nested:</b> {@code subscriptions} and {@code products} sub-sections</li>
 * </ul>
 * If the {@code subscriptions} sub-section is present, it takes precedence over flat fields.
 *
 * <p>Inject into other plugins with {@code @Inject("stripeConfig")}.
 */
@RegisterPlugin(
        name = "stripeConfig",
        description = "Provides StripeConfigData loaded from the stripeConfig YAML block",
        enabledByDefault = false)
public class StripeConfig implements Provider<StripeConfigData> {

    private static final String MONGO_REALM_AUTHENTICATOR = "mongoRealmAuthenticator";

    @Inject("config")
    private Map<String, Object> config;

    @Inject("rh-config")
    private Configuration rhConfig;

    private StripeConfigData data;

    @OnInit
    @SuppressWarnings("unchecked")
    public void onInit() {
        var subscriptions = parseSubscriptions();
        var products = parseProducts();

        data = new StripeConfigData(
                configVal(config, "secret-key", ""),
                configVal(config, "webhook-secret", ""),
                db(),
                configVal(config, "teams-collection", "teams"),
                subscriptions,
                products);
    }

    /**
     * Parses subscriptions config. Supports both nested ({@code subscriptions: {...}})
     * and flat (fields at top level) formats. Nested takes precedence.
     */
    @SuppressWarnings("unchecked")
    private SubscriptionsConfig parseSubscriptions() {
        Map<String, Object> subMap = null;
        boolean nested = false;

        // Check for nested format first
        if (config.get("subscriptions") instanceof Map<?, ?> m) {
            subMap = (Map<String, Object>) m;
            nested = true;
        }

        // Source of truth for each field: nested sub-section or flat top level
        var source = nested ? subMap : config;
        var enabled = configVal(source, "enabled", true);

        var plansConf = source.get("plans") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.<String, Object>of();

        var plans = new LinkedHashMap<String, PlanConfig>();
        for (var entry : plansConf.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> planMap) {
                plans.put(entry.getKey(), parsePlan((Map<String, Object>) planMap));
            }
        }

        var notificationsConf = source.get("notifications") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.<String, Object>of();
        var templatesConf = source.get("templates") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.<String, Object>of();

        var notifications = new HashMap<String, NotificationConfig>();
        for (var name : new String[]{
                NotificationConfig.PAYMENT_FAILED,
                NotificationConfig.TRIAL_WILL_END,
                NotificationConfig.SUBSCRIPTION_CANCELED,
                NotificationConfig.OVER_LIMIT
        }) {
            var nEnabled = NotificationConfig.defaultEnabled(name);
            if (notificationsConf.get(name) instanceof Map<?, ?> nm && nm.get("enabled") instanceof Boolean b) {
                nEnabled = b;
            }
            var template = templatesConf.get(name) instanceof String s && !s.isBlank() ? s : null;
            notifications.put(name, new NotificationConfig(nEnabled, template));
        }

        return new SubscriptionsConfig(
                enabled,
                plans,
                configVal(source, "default-plan", "free"),
                configVal(source, "default-trial-period-days", 0),
                configVal(source, "success-url", ""),
                configVal(source, "cancel-url", ""),
                configVal(source, "portal-return-url", ""),
                notifications);
    }

    /**
     * Parses products config from the {@code products} sub-section.
     * Returns {@code null} if the sub-section is absent (products mode not configured).
     */
    @SuppressWarnings("unchecked")
    private ProductsConfig parseProducts() {
        if (!(config.get("products") instanceof Map<?, ?> m)) {
            return null;
        }

        var prodMap = (Map<String, Object>) m;
        var enabled = configVal(prodMap, "enabled", false);

        var shippingOptions = new ArrayList<ProductsConfig.ShippingOption>();
        if (prodMap.get("shipping-options") instanceof List<?> list) {
            for (var item : list) {
                if (item instanceof Map<?, ?> shipMap) {
                    var displayName = configVal(shipMap, "display-name", "");
                    var amount = configVal(shipMap, "amount", 0L);
                    var deliveryMap = shipMap.get("delivery-estimate-days") instanceof Map<?, ?> dm
                            ? (Map<String, Object>) dm : Map.<String, Object>of();
                    var delivery = new ProductsConfig.ShippingOption.DeliveryEstimateDays(
                            configVal(deliveryMap, "minimum", 0),
                            configVal(deliveryMap, "maximum", 0));
                    shippingOptions.add(new ProductsConfig.ShippingOption(displayName, amount, delivery));
                }
            }
        }

        var orderNotifications = new HashMap<String, ProductsConfig.OrderNotificationConfig>();
        if (prodMap.get("notifications") instanceof Map<?, ?> nm) {
            for (var name : new String[]{"order-confirmed", "order-refunded"}) {
                if (nm.get(name) instanceof Map<?, ?> onm && onm.get("enabled") instanceof Boolean b) {
                    orderNotifications.put(name, new ProductsConfig.OrderNotificationConfig(b));
                }
            }
        }

        return new ProductsConfig(
                enabled,
                configVal(prodMap, "init-enabled", true),
                configVal(prodMap, "catalog-collection", "catalog"),
                configVal(prodMap, "orders-collection", "orders"),
                configVal(prodMap, "transactions-collection", "transactions"),
                configVal(prodMap, "inventory-collection", (String) null),
                configVal(prodMap, "default-currency", "eur"),
                configVal(prodMap, "buyer-email-field", "_id"),
                configVal(prodMap, "invoice-team-orders", true),
                configVal(prodMap, "collect-tax-id", true),
                configVal(prodMap, "success-url", ""),
                configVal(prodMap, "cancel-url", ""),
                configVal(prodMap, "session-expires-minutes", 60),
                configVal(prodMap, "max-line-items", 50),
                configVal(prodMap, "max-quantity-per-line", 100),
                configVal(prodMap, "automatic-tax", true),
                shippingOptions,
                orderNotifications);
    }

    @SuppressWarnings("unchecked")
    private PlanConfig parsePlan(Map<String, Object> planMap) {
        var seatsMap = planMap.get("seats") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        var seats = parseSeats(seatsMap);

        var limits = planMap.get("limits") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.<String, Object>of();

        return new PlanConfig(
                configVal(planMap, "price-id-monthly", null),
                configVal(planMap, "price-id-annual", null),
                configVal(planMap, "trial-period-days", (Integer) null),
                seats,
                limits);
    }

    private SeatsConfig parseSeats(Map<String, Object> seatsMap) {
        if (seatsMap == null) {
            return new SeatsConfig(SeatsMode.UNLIMITED, null);
        }
        var modeStr = configVal(seatsMap, "mode", "unlimited");
        SeatsMode mode;
        try {
            mode = SeatsMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            mode = SeatsMode.UNLIMITED;
        }
        var max = configVal(seatsMap, "max", (Integer) null);
        return new SeatsConfig(mode, max);
    }

    /**
     * MongoDB database containing the teams collection, sourced from
     * {@code mongoRealmAuthenticator/users-db} so that this module always reads and
     * writes team documents where {@code restheart-accounts} writes them.
     */
    private String db() {
        return configVal(mongoRealmAuthenticatorConf(), "users-db", "restheart");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mongoRealmAuthenticatorConf() {
        return rhConfig != null && rhConfig.toMap().get(MONGO_REALM_AUTHENTICATOR) instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : null;
    }

    @Override
    public StripeConfigData get(PluginRecord<?> caller) {
        return data;
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T> T configVal(Map<?, ?> map, String key, T defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        try {
            return (T) map.get(key);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
}
