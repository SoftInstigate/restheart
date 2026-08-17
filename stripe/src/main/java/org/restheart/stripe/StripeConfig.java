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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.restheart.configuration.Configuration;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.stripe.NotificationConfig;
import org.restheart.plugins.stripe.PlanConfig;
import org.restheart.plugins.stripe.SeatsConfig;
import org.restheart.plugins.stripe.SeatsMode;
import org.restheart.plugins.stripe.StripeConfigData;

/**
 * RESTHeart {@link Provider} that reads the {@code stripeConfig} YAML block and exposes
 * a {@link StripeConfigData} instance to other plugins via DI.
 *
 * <p>{@code db} is sourced from {@code mongoRealmAuthenticator/users-db}, for the same
 * reason {@code accountsConfig} sources its own {@code db} from there — see
 * {@link StripeConfigData}'s class javadoc.
 *
 * <p>Required-field validation (non-blank {@code secret-key} / {@code webhook-secret})
 * is not performed here — it is {@code stripeInitializer}'s job, so that a blank value
 * fails startup with a message naming the missing keys rather than surfacing later as a
 * Stripe {@code 401} on the first checkout.
 *
 * <p>Expected YAML configuration:
 * <pre>{@code
 * stripeConfig:
 *   secret-key:                $(STRIPE_SECRET_KEY)
 *   webhook-secret:             $(STRIPE_WEBHOOK_SECRET)
 *   default-plan:               free
 *   default-trial-period-days:  0
 *   teams-collection:           teams
 *   success-url:                https://app.example.com/billing?success=true
 *   cancel-url:                 https://app.example.com/billing?canceled=true
 *   portal-return-url:          https://app.example.com/billing
 *   plans:
 *     free:
 *       seats: { mode: capped, max: 1 }
 *     gold:
 *       price-id-monthly: price_xxx
 *       price-id-annual:  price_yyy
 *       trial-period-days: 30
 *       seats: { mode: capped, max: 10 }
 *       limits: { max-projects: 50 }
 *   notifications:
 *     payment-failed:        { enabled: false }
 *     trial-will-end:        { enabled: false }
 *     subscription-canceled: { enabled: true }
 *     over-limit:             { enabled: true }
 *   templates:
 *     payment-failed:        etc/email-templates/stripe/payment-failed.html
 * }</pre>
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
        var plansConf = config != null && config.get("plans") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.<String, Object>of();

        var plans = new LinkedHashMap<String, PlanConfig>();
        for (var entry : plansConf.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> planMap) {
                plans.put(entry.getKey(), parsePlan((Map<String, Object>) planMap));
            }
        }

        var notificationsConf = config != null && config.get("notifications") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.<String, Object>of();
        var templatesConf = config != null && config.get("templates") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.<String, Object>of();

        var notifications = new HashMap<String, NotificationConfig>();
        for (var name : new String[]{
                NotificationConfig.PAYMENT_FAILED,
                NotificationConfig.TRIAL_WILL_END,
                NotificationConfig.SUBSCRIPTION_CANCELED,
                NotificationConfig.OVER_LIMIT
        }) {
            var enabled = NotificationConfig.defaultEnabled(name);
            if (notificationsConf.get(name) instanceof Map<?, ?> nm && nm.get("enabled") instanceof Boolean b) {
                enabled = b;
            }
            var template = templatesConf.get(name) instanceof String s && !s.isBlank() ? s : null;
            notifications.put(name, new NotificationConfig(enabled, template));
        }

        data = new StripeConfigData(
                configVal(config, "secret-key", ""),
                configVal(config, "webhook-secret", ""),
                plans,
                configVal(config, "default-plan", "free"),
                configVal(config, "default-trial-period-days", 0),
                db(),
                configVal(config, "teams-collection", "teams"),
                configVal(config, "success-url", ""),
                configVal(config, "cancel-url", ""),
                configVal(config, "portal-return-url", ""),
                notifications);
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
