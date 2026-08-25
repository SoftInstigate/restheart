/*-
 * ========================LICENSE_START=================================
 * restheart-commons
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
package org.restheart.plugins.stripe;

import java.util.List;
import java.util.Map;

/**
 * Products mode configuration, nested under {@code stripeConfig.products}.
 *
 * @param enabled               whether the products mode is active
 * @param initEnabled           whether the automatic initializer creates collections/indexes/schema;
 *                              set to {@code false} for on-demand init via {@code StripeInitService}
 * @param catalogCollection     MongoDB collection holding the product catalog
 * @param ordersCollection      MongoDB collection for orders
 * @param transactionsCollection MongoDB collection for the transactions ledger
 * @param inventoryCollection   MongoDB collection for stock data, or {@code null} to disable stock checks
 * @param defaultCurrency       default currency code (e.g. {@code "eur"})
 * @param buyerEmailField       user-document field holding the buyer's email; {@code null} if users have none
 * @param invoiceTeamOrders     whether to issue Stripe invoices for team-paid orders
 * @param collectTaxId          whether to collect VAT/tax id for team-paid orders
 * @param successUrl            Stripe Checkout success redirect URL
 * @param cancelUrl             Stripe Checkout cancel redirect URL
 * @param sessionExpiresMinutes checkout session expiry in minutes
 * @param maxLineItems          maximum number of line items per cart
 * @param maxQuantityPerLine    maximum quantity per line item
 * @param automaticTax          whether to enable Stripe Tax
 * @param shippingOptions       shipping options offered for carts containing physical products
 * @param shippingAddressCountries ISO 3166-1 alpha-2 codes Stripe Checkout will accept a shipping
 *                              address in. Empty means no address is collected — Stripe has no
 *                              "anywhere" setting, so a list is the only way to ask at all.
 * @param orderNotifications    notification configuration for orders (order-confirmed, order-refunded)
 */
public record ProductsConfig(
        boolean enabled,
        boolean initEnabled,
        String catalogCollection,
        String ordersCollection,
        String transactionsCollection,
        String inventoryCollection,
        String defaultCurrency,
        String buyerEmailField,
        boolean invoiceTeamOrders,
        boolean collectTaxId,
        String successUrl,
        String cancelUrl,
        int sessionExpiresMinutes,
        int maxLineItems,
        int maxQuantityPerLine,
        boolean automaticTax,
        List<ShippingOption> shippingOptions,
        List<String> shippingAddressCountries,
        Map<String, OrderNotificationConfig> orderNotifications) {

    /**
     * A shipping option offered for carts containing physical products.
     *
     * @param displayName          display name shown to the customer
     * @param amount               shipping cost in smallest currency unit
     * @param deliveryEstimateDays delivery estimate in days (minimum and maximum)
     */
    public record ShippingOption(
            String displayName,
            long amount,
            DeliveryEstimateDays deliveryEstimateDays) {

        public record DeliveryEstimateDays(int minimum, int maximum) {}
    }

    /**
     * Notification configuration for order events.
     *
     * @param enabled      whether this notification is sent
     * @param templatePath path to the HTML template, or {@code null} to use the built-in — same
     *                     meaning {@code templatePath} has on {@link NotificationConfig}, the
     *                     equivalent record for subscription notifications. A request-scoped
     *                     inline override still wins over this when present; see
     *                     {@code RequestOverrides.templateInline()} in {@code restheart-stripe}.
     */
    public record OrderNotificationConfig(boolean enabled, String templatePath) {}
}
