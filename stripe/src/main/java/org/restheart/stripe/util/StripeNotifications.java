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

import java.io.IOException;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

import org.restheart.emails.EmailRenderer;
import org.restheart.emails.EmailSender;
import org.restheart.emails.EmailTemplateLoader;
import org.restheart.exchange.ServiceRequest;
import org.restheart.plugins.stripe.StripeConfigData;
import org.restheart.plugins.stripe.SubscriptionOwner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends the four billing notification emails, following the same
 * {@code EmailTemplateLoader} + {@code EmailRenderer} pipeline as {@code restheart-accounts}.
 *
 * <p>⚠️ {@code payment-failed} and {@code trial-will-end} default to disabled — Stripe can
 * send its own equivalent from the Dashboard ("Subscriptions and emails"), and duplication
 * cannot be detected at runtime. See {@link org.restheart.plugins.stripe.NotificationConfig}.
 *
 * <p>Idempotency is not handled here: callers in {@code StripeWebhookService} only call
 * {@link #send} after a conditional state write has confirmed the triggering event was not
 * a stale redelivery — see that class for why a successful {@code writeSubscription} /
 * {@code patchSubscription} is sufficient proof of a genuine transition.
 */
public final class StripeNotifications {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeNotifications.class);

    private StripeNotifications() {
    }

    /**
     * Sends a notification email if that notification is enabled for the effective
     * (possibly per-tenant) configuration, and the entity has a billing contact email.
     *
     * @param emailSender the injected {@code emails} provider
     * @param req         the current request — the webhook is a request too, so per-tenant
     *                    template and SMTP overrides resolve normally
     * @param conf        the effective configuration
     * @param name        the notification name — see {@link org.restheart.plugins.stripe.NotificationConfig}
     * @param owner       the entity to notify
     * @param vars        template variables beyond {@code app-name}/{@code year}/{@code team-name}/
     *                    {@code billing-url}, which are filled in here
     */
    public static void send(EmailSender emailSender, ServiceRequest<?> req, StripeConfigData conf,
            String name, SubscriptionOwner owner, Map<String, String> vars) {
        if (!RequestOverrides.notificationEnabled(req, conf, name)) {
            return;
        }
        if (owner.ownerEmail() == null || owner.ownerEmail().isBlank()) {
            LOGGER.warn("[stripe] cannot send '{}' notification: entity {} has no billing email", name, owner.id());
            return;
        }
        if (emailSender == null || !emailSender.isEnabled()) {
            LOGGER.warn("[stripe] '{}' notification for entity {} not sent: no email sender configured",
                    name, owner.id());
            return;
        }

        var inline = RequestOverrides.templateInline(req, name);
        var path = conf.notification(name).templatePath();

        try {
            var raw = EmailTemplateLoader.loadWithFallback(inline, path, name + ".html");

            var allVars = new HashMap<>(vars);
            allVars.putIfAbsent("year", String.valueOf(Year.now().getValue()));
            allVars.putIfAbsent("app-name", "App");
            allVars.putIfAbsent("team-name", owner.displayName() != null ? owner.displayName() : "");
            allVars.putIfAbsent("billing-url", RequestOverrides.portalReturnUrl(req, conf));

            var rendered = EmailRenderer.render(raw, allVars, "en");
            var recipientName = owner.displayName() != null ? owner.displayName() : owner.ownerEmail();

            emailSender.sendEmailAsync(req, owner.ownerEmail(), recipientName, rendered.subject(), rendered.htmlBody());
        } catch (IOException e) {
            LOGGER.error("[stripe] failed to load '{}' notification template: {}", name, e.getMessage());
        }
    }
}
