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
package org.restheart.stripe.util;

import java.util.Map;

import org.restheart.exchange.ServiceRequest;
import org.restheart.plugins.stripe.BillingScope;
import org.restheart.plugins.stripe.PlanConfig;
import org.restheart.plugins.stripe.ProductsConfig;
import org.restheart.plugins.stripe.StripeConfigData;

/**
 * Reads per-request override parameters and returns the effective values, falling back
 * to the plugin's static {@link StripeConfigData}. Follows the same pattern as
 * {@code restheart-accounts}' own {@code RequestOverrides}.
 *
 * <h2>Multi-tenant usage</h2>
 * <p>An interceptor such as a deployment's {@code TenantConfigInterceptor} reads the
 * per-tenant configuration document from MongoDB and attaches these params at
 * {@code REQUEST_BEFORE_EXCHANGE_INIT}.
 *
 * <h2>Single-tenant usage</h2>
 * <p>When no interceptor attaches override params, all methods return the values from
 * {@link StripeConfigData}, preserving backward compatibility.
 *
 * <h2>Soft coupling to {@code restheart-accounts}</h2>
 * <p>{@link #effectiveOwnershipRole(ServiceRequest, String)} reads
 * {@code override-accounts-ownership-role} — the attached-parameter key
 * {@code restheart-accounts}' own {@code RequestOverrides} uses. This module cannot
 * depend on {@code restheart-accounts} at compile time (a deployment may not run it at
 * all), so the coupling is a shared naming convention rather than a shared Java type.
 * If the key is absent — because accounts is not present, not overriding it, or a future
 * version renames it — this simply falls back to the static value.
 *
 * <h2>{@code override-stripe-products}</h2>
 * <p>Same replace-wholesale contract as {@link #PLANS} — see {@link #products}. A tenant
 * without products mode simply never has this attached, and {@link #products} falls back to
 * {@code conf.products()}, which is {@code null} in that case exactly as it is statically.
 *
 * <h2>Multi-tenant kill switches ({@code rh-} prefix, not {@code override-})</h2>
 * <p>Distinct from the overrides above by both prefix and meaning: an override replaces a
 * value, a kill switch removes the module from the request entirely. Presence of the
 * parameter disables — the value itself is never inspected, so attaching
 * {@code Boolean.FALSE} still disables.
 * <ul>
 *   <li>{@link #SUBSCRIPTIONS_DISABLED} ({@code rh-stripe-subscriptions-disabled}) — read by
 *   every subscriptions-mode {@code Service} ({@code stripeCheckoutService},
 *   {@code stripePortalService}, {@code stripeSubscriptionService}, {@code stripePlansService},
 *   {@code stripeLicensesService}, {@code stripeWebhookService}) and by
 *   {@code SubscriptionVarResolver} (where a disabled tenant resolves {@code @subscription}
 *   to unresolved, the same outcome as any other resolution failure).</li>
 *   <li>{@link #PRODUCTS_DISABLED} ({@code rh-stripe-products-disabled}) — read by
 *   {@code OrdersCheckoutInterceptor} and {@code OrdersCheckoutResponseInterceptor} in their
 *   {@code resolve()}, so the interceptor never runs at all for a disabled tenant.</li>
 * </ul>
 * <p>A deployment such as RESTHeart Cloud attaches whichever switch applies for a tenant
 * that has not installed, or has disabled, the corresponding mode — instead of building a
 * separate access gate. A tenant with an incomplete configuration (e.g. installed but no
 * {@code secret-key} yet) is a different case and is not covered by these switches: a
 * deployment should answer that with its own {@code 503}, not by disabling the module,
 * since disabling would misreport "not installed" for a tenant that is mid-setup.
 */
public final class RequestOverrides {

    // ── stripeConfig overrides ──────────────────────────────────────────────

    public static final String SECRET_KEY = "override-stripe-secret-key";
    public static final String WEBHOOK_SECRET = "override-stripe-webhook-secret";

    /** Replaces the whole plan catalog; see {@link #plans(ServiceRequest, StripeConfigData)}. */
    public static final String PLANS = "override-stripe-plans";
    public static final String DEFAULT_PLAN = "override-stripe-default-plan";

    /** Replaces the whole products configuration; see {@link #products(ServiceRequest, StripeConfigData)}. */
    public static final String PRODUCTS = "override-stripe-products";

    public static final String DB = "override-stripe-db";
    public static final String TEAMS_COLLECTION = "override-stripe-teams-collection";

    public static final String SUCCESS_URL = "override-stripe-success-url";
    public static final String CANCEL_URL = "override-stripe-cancel-url";
    public static final String PORTAL_RETURN_URL = "override-stripe-portal-return-url";

    /**
     * {@code override-stripe-tmpl-{name}} — inline HTML for a notification template.
     *
     * <p>Generic on {@code name}: it covers subscription notifications
     * ({@link org.restheart.plugins.stripe.NotificationConfig#PAYMENT_FAILED} and friends) and
     * order notifications ({@code order-confirmed}, {@code order-refunded}) alike, with no
     * per-mode wiring needed on either side — {@link #templateInline} does not know or care which
     * mode a given name belongs to, and neither does the caller that attaches it.
     */
    public static final String TMPL_PREFIX = "override-stripe-tmpl-";

    /** {@code override-stripe-notify-{name}-enabled}. */
    public static final String NOTIFY_ENABLED_PREFIX = "override-stripe-notify-";
    public static final String NOTIFY_ENABLED_SUFFIX = "-enabled";

    /** Soft-coupled to restheart-accounts — see class javadoc. */
    private static final String ACCOUNTS_OWNERSHIP_ROLE_OVERRIDE = "override-accounts-ownership-role";

    // ── Multi-tenant kill switches ──────────────────────────────────────────

    /** Presence (any value) disables every subscriptions-mode service and {@code @subscription}; see class javadoc. */
    public static final String SUBSCRIPTIONS_DISABLED = "rh-stripe-subscriptions-disabled";

    /** Presence (any value) disables the products-mode interceptors; see class javadoc. */
    public static final String PRODUCTS_DISABLED = "rh-stripe-products-disabled";

    private RequestOverrides() {
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    /** Effective Stripe secret key. ⚠️ Must be passed explicitly via {@code RequestOptions} to every SDK call — never through {@code Stripe.apiKey}. */
    public static String secretKey(ServiceRequest<?> req, StripeConfigData conf) {
        return str(req, SECRET_KEY, conf.secretKey());
    }

    /** Effective webhook signing secret. */
    public static String webhookSecret(ServiceRequest<?> req, StripeConfigData conf) {
        return str(req, WEBHOOK_SECRET, conf.webhookSecret());
    }

    /**
     * Effective plan catalog. The override, when present, must be a fully-parsed
     * {@code Map<String, PlanConfig>} attached by the deployment's interceptor — not a raw
     * YAML fragment — and replaces the catalog wholesale rather than merging into it: a
     * partial override would leave a tenant on a mix of two catalogs, where a plan id
     * present in one and not the other resolves differently depending on which half answered.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, PlanConfig> plans(ServiceRequest<?> req, StripeConfigData conf) {
        var v = req.attachedParam(PLANS);
        return (v instanceof Map<?, ?> m && !m.isEmpty()) ? (Map<String, PlanConfig>) m : conf.plans();
    }

    /** Effective default plan id. */
    public static String defaultPlan(ServiceRequest<?> req, StripeConfigData conf) {
        return str(req, DEFAULT_PLAN, conf.defaultPlan());
    }

    /**
     * Effective products configuration. Same contract as {@link #plans}: the override, when
     * present, must be a fully-parsed {@link ProductsConfig} attached by the deployment's
     * interceptor, and replaces the static configuration wholesale — there is no field-by-field
     * merging with {@code conf.products()}.
     *
     * <p>{@code null} both when the override is absent and {@code conf.products()} is itself
     * {@code null} (products mode not configured at all) — callers already treat a {@code null}
     * result as "products mode is off", so this does not introduce a new case.
     */
    public static ProductsConfig products(ServiceRequest<?> req, StripeConfigData conf) {
        var v = req.attachedParam(PRODUCTS);
        return (v instanceof ProductsConfig pc) ? pc : conf.products();
    }

    /**
     * Effective MongoDB database. Binds the <em>default</em> {@code SubscriptionOwnerProvider}
     * only — a custom provider receives the resulting {@link BillingScope} and is free to
     * ignore it.
     */
    public static String db(ServiceRequest<?> req, StripeConfigData conf) {
        return str(req, DB, conf.db());
    }

    /** Effective teams collection name. Same scope-only caveat as {@link #db}. */
    public static String teamsCollection(ServiceRequest<?> req, StripeConfigData conf) {
        return str(req, TEAMS_COLLECTION, conf.teamsCollection());
    }

    /** Effective {@link BillingScope} for this request, built from {@link #db} / {@link #teamsCollection}. */
    public static BillingScope scope(ServiceRequest<?> req, StripeConfigData conf) {
        return new BillingScope(db(req, conf), teamsCollection(req, conf));
    }

    /** Effective Checkout success redirect URL. */
    public static String successUrl(ServiceRequest<?> req, StripeConfigData conf) {
        return str(req, SUCCESS_URL, conf.successUrl());
    }

    /** Effective Checkout cancel redirect URL. */
    public static String cancelUrl(ServiceRequest<?> req, StripeConfigData conf) {
        return str(req, CANCEL_URL, conf.cancelUrl());
    }

    /** Effective Customer Portal return URL. */
    public static String portalReturnUrl(ServiceRequest<?> req, StripeConfigData conf) {
        return str(req, PORTAL_RETURN_URL, conf.portalReturnUrl());
    }

    /** Whether {@link #SUBSCRIPTIONS_DISABLED} is attached to this request — see class javadoc. */
    public static boolean subscriptionsDisabled(ServiceRequest<?> req) {
        return req.attachedParam(SUBSCRIPTIONS_DISABLED) != null;
    }

    /** Whether {@link #PRODUCTS_DISABLED} is attached to this request — see class javadoc. */
    public static boolean productsDisabled(ServiceRequest<?> req) {
        return req.attachedParam(PRODUCTS_DISABLED) != null;
    }

    /**
     * Inline HTML override for a notification template, or {@code null} if not overridden.
     * {@code notificationName} may name a subscription or an order notification — see
     * {@link #TMPL_PREFIX}.
     */
    public static String templateInline(ServiceRequest<?> req, String notificationName) {
        var v = req.attachedParam(TMPL_PREFIX + notificationName);
        return (v instanceof String s && !s.isBlank()) ? s : null;
    }

    /** Effective enabled flag for a notification. */
    public static boolean notificationEnabled(ServiceRequest<?> req, StripeConfigData conf, String notificationName) {
        var v = req.attachedParam(NOTIFY_ENABLED_PREFIX + notificationName + NOTIFY_ENABLED_SUFFIX);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return conf.notification(notificationName).enabled();
    }

    /**
     * Effective ownership role. Soft-coupled to {@code restheart-accounts}' own
     * {@code override-accounts-ownership-role} — see class javadoc.
     *
     * @param staticOwnershipRole the static fallback, typically {@code AccountsConfigData#ownershipRole()}
     */
    public static String effectiveOwnershipRole(ServiceRequest<?> req, String staticOwnershipRole) {
        return str(req, ACCOUNTS_OWNERSHIP_ROLE_OVERRIDE, staticOwnershipRole);
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private static String str(ServiceRequest<?> req, String key, String defaultValue) {
        var v = req.attachedParam(key);
        return (v instanceof String s && !s.isBlank()) ? s : defaultValue;
    }
}
