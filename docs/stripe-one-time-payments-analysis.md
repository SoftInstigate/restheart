# `restheart-stripe`: the products mode

**Status:** analysis / proposal — not implemented
**Target:** 9.8.0, which ships **both** modes — subscriptions and products.

---

## 1. Scope

**Payments only.** The module prices a cart, takes the money through Stripe Checkout, and records faithfully what happened. Everything downstream — fulfilment, shipping, stock management, returns — belongs to the deployment, working off the collections the module writes.

**Depends on `restheart-accounts`.** The billing entity is always the team. A user who needs a personal billing profile creates a one-person team; a company is a multi-member team. The UI decides how to present "team" to the end user — "billing profile", "organization", "account" — the module does not care.

The entities involved in payment are **given**: fixed schemas the module defines. No field mapping, no catalog SPI, no buyer SPI. Same stance `restheart-accounts` takes toward users and teams.

| Collection | Module's role |
|---|---|
| `catalog` | **Read** — the sole authority for prices |
| `orders` | **Write** — one document per checkout |
| `transactions` | **Write** — append-only ledger of money movements |
| `inventory` | **Read, optional** — refuse to sell what is out of stock |

**In scope:** cart pricing · Checkout session creation · order recording · payment/refund ledger · guest (unauthenticated) checkout · `physical | digital` types · Stripe Tax and shipping options.

**Out of scope:** stock reservation, fulfilment status, shipping/tracking, returns, restocking, warehouses. All resolvable client-side against the same collections.

**No custom service endpoints.** Orders are an ordinary MongoDB collection exposed by RESTHeart's own API; the logic lives in interceptors and the authorization in ACL permissions — see §9.

---

## 2. Executive summary

The Stripe *plumbing* transfers almost entirely. The *domain model* does not.

`restheart-stripe` is built around one assumption visible in nearly every class: **an entity has one long-lived, mutable subscription state.** One team, one `subscription` sub-document, updated in place, guarded by a last-applied timestamp.

An order is the structural opposite: **many short-lived records**, created once and then transitioning through a small monotonic lifecycle. That difference invalidates the persistence pattern, the idempotency mechanism, the ACL variable and the authorization gate — while leaving configuration, multi-tenancy, signature verification and notifications fully reusable.

Because orders are a plain collection, most of what would be endpoint code is not written at all: reads, filtering, pagination and per-user authorization are existing RESTHeart machinery. What remains is one request interceptor, one response interceptor, webhook handlers, and a JSON schema.

**Biggest risk:** price authority (§7.1).

---

## 3. What the current architecture assumes

| Assumption | Where it lives | Holds for orders? |
|---|---|---|
| One subscription per entity | `SubscriptionState`, `TeamRepository` | ❌ Many orders per buyer |
| State is mutable, updated in place | `writeSubscription` / `patchSubscription` | ❌ Short-lived records |
| Ordering resolved by last-applied timestamp | `staleGuard()` | ❌ Wrong tool — §6.3 |
| Catalog is static, in YAML | `stripeConfig.plans`, `PlanConfig` | ❌ A collection |
| Catalog is small (tens) | `StripeCatalogCache` prefetches all plans | ❌ Could be 10⁵ SKUs |
| Buying commits the org to recurring charges | `canManageBilling` gate | Different gate — §7.3 |
| Caller is authenticated | `req.isAuthenticated()` in every service | ❌ Guest checkout — §7.5 |
| `checkout.session.completed` is a no-op | `StripeWebhookService` | ❌ **Inverted** — §6.1 |

The last row matters most. For subscriptions, `checkout.session.completed` is deliberately ignored because Stripe always follows it with `customer.subscription.created` carrying the full state. For one-time payments **there is no follow-up event** — it is the moment the money is confirmed. The module's most deliberate no-op becomes a primary handler.

---

## 4. What is reusable as-is

| Component | Reuse | Note |
|---|---|---|
| `StripeWebhookService` signature verification | ✅ Full | Raw body, `whsec_`, the `400`/`200`/`500` contract |
| `RequestOverrides` | ✅ Full | Add product-mode keys |
| Per-call `RequestOptions` API key | ✅ Full | The global-`Stripe.apiKey` hazard is already solved |
| `StripeIds` | ✅ Full | |
| `StripeNotifications` + `EmailRenderer` | ✅ Full | Order confirmation, refund |
| `StripeConfig` / `StripeConfigData` | ✅ Mechanism | New sub-sections, same provider pattern |
| Karate test harness | ✅ Full | Local HMAC signing proven in `webhook-signature.feature` |
| **RESTHeart MongoDB API** | ✅ Full | Replaces every read endpoint — §9 |
| **`readFilter` / `mergeRequest`** | ✅ Full | Replaces per-order authorization code — §7.4 |
| **`jsonSchema` collection metadata** | ✅ Full | Replaces hand-written document validation |

**Not reusable:** `SubscriptionState`, `SubscriptionView`, `SubscriptionVarResolver`, `Seats`, `SeatsConfig`, `PlanConfig`, `PriceAttribution`, `StripeLicensesService`, `StripeSubscriptionService`, `StripePortalService`, `StripePlansService`, and the subscription half of `TeamRepository`.

**Not reused:** `StripeCatalogCache`. It avoids hitting `api.stripe.com` for display data belonging to *configured* plans. A MongoDB catalog is already local, and the cache's shape does not fit a large, frequently-edited collection.

**Fully reusable:** `CustomerProvisioning` — the same lazy, atomic, idempotent pattern for team-level Customers, shared with the subscriptions mode (§7.6).

---

## 5. Domain model

### 5.1 Product types

`physical` or `digital`. One discriminator, three consequences:

| | `physical` | `digital` |
|---|---|---|
| Shipping address + `shipping_options` on the session | yes | no |
| `inventory` consulted | yes | no |
| Everything after payment | deployment's | deployment's |

A cart may mix the two; if any line is physical, the session collects a shipping address.

### 5.2 `catalog` — read only, the price authority

```jsonc
{
  "_id":             "SKU-1234",
  "type":            "physical",              // physical | digital
  "name":            "Blue Widget",
  "description":     "Anodised, 40mm",        // optional
  "image_url":       "https://cdn.example.com/w.jpg",   // optional
  "unit_amount":     2500,                    // integer, smallest currency unit
  "currency":        "eur",                   // optional, defaults to products.default-currency
  "purchasable":     true,
  "tax_code":        "txcd_99999999",         // optional, used when automatic-tax is on
  "stripe_price_id": null                     // optional, see below
}
```

Required: `_id`, `type`, `name`, `unit_amount`, `purchasable`.

The module never writes here. Catalog CRUD is served by RESTHeart's MongoDB API with no code from this module.

**`stripe_price_id`** is an escape hatch. Absent (the normal case) → the module builds an ad-hoc `price_data` line item, so no product need exist in Stripe. Present → used as a real Stripe `Price`.

Verified against the pinned SDK (`stripe-java` 33.3.0): `SessionCreateParams.LineItem.PriceData` supports `setUnitAmount`, `setCurrency` and `setProductData(...)` with `setName`, `setDescription`, `addImage`, `setTaxCode`. Nothing requires syncing the catalog into Stripe.

**⚠️ Validate on read.** `unit_amount` must be a non-negative integer. `unit_amount: 25.00`, written by someone thinking in euros, charges €0.25. Refuse the product by name rather than rounding:

```
[stripe] product SKU-1234 has a non-integer unit_amount (25.0) — refusing to sell it
```

`unit_amount` is Stripe's own term; `price_cents` would be wrong for zero-decimal currencies such as JPY.

**⚠️ One currency per cart.** A session has exactly one currency. Reject a mixed-currency cart with `400` rather than letting Stripe fail.

### 5.3 `orders`

```jsonc
{
  "_id":               ObjectId,                // the order id, client-visible
  "stripe_session_id": "cs_live_a1b2...",       // UNIQUE INDEX — idempotency, client-visible
  "stripe_payment_intent": "pi_...",

  "secret":      "6f2c...",                     // high entropy — guest access, §7.5
  "checkout_url": "https://checkout.stripe.com/...",

  "buyer_id":    "alice@example.com",           // who placed it — null for a guest
  "buyer_email": "alice@example.com",           // where the confirmation goes
  "payer": {                                    // who is billed — §7.6
    "type":               "team",               // team | guest
    "id":                 ObjectId,             // team id — null for a guest
    "stripe_customer_id": "cus_..."
  },

  "status":            "pending_payment",       // pending_payment | paid | failed | expired
  "requires_shipping": true,

  // snapshot — never references, §7.2
  "line_items": [
    { "product_id": "SKU-1234", "type": "physical", "name": "Blue Widget",
      "unit_amount": 2500, "quantity": 2, "subtotal": 5000, "tax_code": "txcd_99999999" }
  ],

  "currency":        "eur",
  "amount_subtotal": 5000,
  "amount_tax":      1100,     // filled from Stripe once paid
  "amount_shipping": 500,      // filled from Stripe once paid
  "amount_total":    6600,
  "amount_refunded": 0,

  "shipping_address": { /* from session.shipping_details, when requires_shipping */ },

  "created_at": { "$date": 1771200000000 },
  "paid_at":    { "$date": 1771200300000 },
  "expires_at": { "$date": 1771203600000 }   // session expiry; TTL index target, §7.5
}
```

Indexes: unique `stripe_session_id`; `(buyer_id, created_at desc)`; unique `secret`; TTL on `expires_at` **partial to `status: "pending_payment"`** so abandoned carts evaporate and paid orders never do.

There is no `order_number`: `_id` is the ObjectId and `stripe_session_id` is the Stripe-side reference. Sequential human-facing numbering would need a counter document and is a deployment concern.

`status` is payment status only — no `fulfilled` state, since fulfilment is out of scope. A deployment needing one adds its own field to the same document.

### 5.4 `transactions` — append-only money ledger

One order has many money movements. Keeping them out of the order document is what makes the record auditable and reconcilable against Stripe.

```jsonc
{
  "_id":              ObjectId,
  "order_id":         ObjectId,
  "type":             "payment",        // payment | refund | dispute | dispute_reversal
  "amount":           6600,             // positive for payment, negative for refund
  "currency":         "eur",
  "stripe_object_id": "pi_...",         // or re_... / dp_...
  "stripe_event_id":  "evt_...",        // UNIQUE INDEX — idempotency
  "occurred_at":      { "$date": 1771200300000 },   // Stripe's timestamp
  "recorded_at":      { "$date": 1771200301000 }
}
```

The unique index on `stripe_event_id` is the ledger's idempotency: a redelivered webhook cannot double-record a refund.

This yields a checkable invariant the order document alone does not give:

```
sum(transactions.amount where order_id = X) == order.amount_total - order.amount_refunded
```

Worth asserting in tests.

### 5.5 `inventory` — read only, optional

```jsonc
{ "_id": "SKU-1234", "available": 42 }
```

If `inventory-collection` is configured, the module refuses to create a session containing a physical product whose `available` is below the requested quantity, answering `409`. If it is not configured, no stock check happens.

**There is no reservation.** Between order creation and payment, stock can be sold to someone else — overselling is possible and the module does not try to prevent it. Doing that properly needs reservations, expiry and a sweeper: stock control, not payment.

The module never decrements `available`. A deployment wanting stock to move on payment does it from `orders`, via a change stream or a job.

---

## 6. Webhooks

### 6.1 Events

The order already exists (created at `POST /orders`), so these events **update** it rather than create it.

| Event | Handling |
|---|---|
| `checkout.session.completed` | `payment_status == "paid"` → `paid`, fill tax/shipping/buyer_email/address, append `payment` transaction. Otherwise leave `pending_payment` |
| `checkout.session.async_payment_succeeded` | → `paid`, append `payment` transaction |
| `checkout.session.async_payment_failed` | → `failed` |
| `checkout.session.expired` | `pending_payment` → `expired` |
| `charge.refunded` | Append `refund` transaction, update `amount_refunded` |
| `charge.dispute.created` | Append `dispute` transaction |

**⚠️ The two `async_payment_*` events are not optional.** With SEPA debit, bank transfers, Bacs, BLIK and others, `checkout.session.completed` fires with `payment_status: "unpaid"` — the customer has committed but the money has not settled. Only `payment_status == "paid"` means paid.

Card-only testing never surfaces this, which makes it a production-only bug.

Matching is on `stripe_session_id`, which the module wrote when creating the order.

### 6.2 Dispatch

`StripeWebhookService.handle` currently switches over event types in one method. Adding six branches with order semantics makes one class serve two domains.

Introduce a small internal SPI so the service keeps sole responsibility for signature verification and the HTTP contract:

```java
public interface StripeEventHandler {
    Set<String> handledEventTypes();
    void handle(Event event, StripeEventContext ctx);
}
```

An internal structuring device, not a module boundary — see §11.

### 6.3 Idempotency — do **not** reuse the staleness guard

The subscription staleness guard (`last_applied_event_at`) is correct for one mutable state and wrong here: two orders from the same buyer are unrelated, with no "newest wins" between them.

Orders need:

1. **Monotonic status transitions** — assert the current status in the update filter (`status: "pending_payment"` → `paid`), so a redelivered event is a no-op rather than a regression.
2. **Ledger idempotency** — unique index on `transactions.stripe_event_id`; insert and swallow the duplicate-key error.
3. **Creation idempotency** — unique `stripe_session_id`, which also prevents two orders ever pointing at one session.

---

## 7. Design decisions

### 7.1 ⚠️ Price authority

**The client never sends a price.** It sends `{items: [{productId, quantity}]}`; the server resolves every price from `catalog`.

This is the difference between a shop and a free shop, and it is the most commonly shipped vulnerability in this kind of integration, because the naive cart payload — the one a frontend developer writes first — contains prices and works perfectly in testing.

Two mechanisms enforce it, in this order:

**The interceptor builds the document from scratch.** It reads `items`, discards everything else, and writes a fresh order document. A client sending `unit_amount`, `amount_total` or `status: "paid"` cannot influence the result by construction. Any top-level field other than `items` should be **rejected** with `400` rather than ignored, so a frontend bug surfaces instead of being silently absorbed.

**⚠️ The JSON schema validates the *final* document, not the client body.** `jsonSchemaBeforeWrite` is registered at `REQUEST_AFTER_AUTH` with `priority = Integer.MAX_VALUE` — explicitly "execute after any other request interceptor". So it sees the document *after* our interceptor has replaced it. It is therefore a guarantee that every persisted order is well-formed, not a filter on client input. Input validation is the interceptor's job.

A schema written as `{items: [...], additionalProperties: false}` would therefore reject every order the module creates.

Further corollaries:

- Validate quantities as positive integers with per-line and per-cart maxima. `quantity: -1` against a permissive account is a refund-generating machine. These limits matter more than rate limiting, because they are the ones that move money.
- On `checkout.session.completed`, compare the stored `amount_total` against `session.getAmountTotal()`. On mismatch, do not mark clean — flag it. Catches a catalog edited mid-checkout.

### 7.2 Cart validity across time

The session is created from a price snapshot; Stripe honours its own line items, so the customer pays the snapshot price — the safer direction.

- Set `expires_at` (min 30 min, default 24 h) to bound the window, and store it on the order.
- **Snapshot line items onto the order**, not references. An order must stay readable years later, after the product is renamed, repriced or deleted.

### 7.3 ⚠️ `canManageBilling` must not gate purchases

Subscription endpoints gate on it because starting a subscription commits the *organisation* to recurring charges — an owner's decision. Buying a product is a one-time charge, not a recurring commitment.

Products use ACL permissions instead. The default policy restricts purchases to team owners — consistent with subscriptions, but through a different, more granular mechanism.

### 7.4 The buyer, and authorization

Given, not pluggable. No SPI.

- **Authenticated** → `buyer_id` is the principal.
- **Unauthenticated** → `buyer_id` is `null`; the order is reachable by `_id` + `secret`.

Authorization is entirely declarative. Only team owners can purchase; members see nothing by default:

```yaml
# team owners: can buy and see their team's orders
- role: user
  predicate: path-prefix(path="/orders") and equals(@user.team.role, "owner")
  priority: 100
  mongo:
    readFilter:   >
      {"payer.id": "@user.team._id"}
    mergeRequest: >
      {"buyer_id": "@user._id"}

# guests: must know both the _id and the secret
- role: $unauthenticated
  predicate: path(path="/orders") and method(value="POST")
  priority: 100

- role: $unauthenticated
  predicate: path-template(value="/orders/{id}") and method(value="GET")
  priority: 100
  mongo:
    readFilter: >
      {"secret": "@qparams['secret']"}
```

`mergeRequest` stamping `buyer_id` from `@user._id` is tamper-proof by construction: a client cannot claim someone else's order, because the value never comes from the body. The interceptor stamps `payer` from the active team — see §7.6.

`@qparams['key']` is a built-in variable (`BuiltInVarResolvers`) resolved through the same path as `@user`, so it works inside `readFilter`. `GET /orders/{oid}?secret=…` returns the document only when the secret matches; without it, nothing.

**`buyer_email`** is resolved differently per buyer, and Stripe always has the last word — see §7.7.

### 7.5 Guest checkout

Enabled by **ACL configuration, not a config flag.** The interceptor does not require authentication; whether anonymous callers reach `/orders` at all is the deployment's ACL decision. Omit the `$unauthenticated` rules above and the shop is customers-only. No `guest-checkout: true` key, no second code path.

**What this exposes.** Unlike the previous design, `POST /orders` *does* write to the database before payment, so abandoned and malicious carts leave documents behind.

Two cheap controls, both already in the model:

- **TTL index on `expires_at`, partial to `status: "pending_payment"`.** Abandoned orders delete themselves at session expiry; paid orders are never touched because the partial filter excludes them. This is the whole mitigation, and it is one index.
- Cart size and quantity limits (§7.1) bound per-request cost.

The realistic attack is not "an attacker sends us money" — it is many sessions created and never paid, consuming Stripe API rate limit and therefore returning `429` to *real* customers. Rate limiting at the proxy is worth adding for a real shop; RESTHeart ships no rate limiter to configure here.

**The `secret`.** Generated by the interceptor with a CSPRNG and returned once in the creation response. It is a bearer credential in a URL, so: unique index, no enumeration, and worth pairing with `expires_at` if guest access should not be indefinite. RESTHeart's `@rnd(bits)` could generate it declaratively via `mergeRequest`, but the interceptor already builds the document and needs the value for the response.

### 7.6 The payer, and Stripe Customers

**The billing entity is always the team.** `restheart-stripe` depends on `restheart-accounts`; every authenticated user belongs to at least one team. A user who needs a personal billing profile creates a one-person team. A company is a multi-member team. The UI decides how to present "team" to the end user — the module does not care.

| Payer | Stripe Customer | Stripe document |
|---|---|---|
| Team | The team's Customer — the same one the subscriptions mode uses | Invoice |
| Guest | None — `customer_email` only | Receipt |

A user who belongs to N teams has **N billing profiles**, one per team, each with its own Stripe Customer and billing identity. Switching the active team in the JWT switches which Customer is used.

**The team is the caller's active team from the JWT claim.** Never a team id taken from the body, which would let a caller charge an organisation they do not belong to.

**Only team owners can purchase.** The ACL enforces this (§7.4). This is consistent with subscriptions: starting a subscription or buying a product both commit the team's billing identity.

**Customers are created lazily, at first purchase — never at registration.** Same rule and same reason as the subscriptions mode: it avoids coupling signup to Stripe being reachable, and avoids sending a Customer object to a third party for every account that never buys.

The team's `stripe_customer_id` is shared with the subscriptions mode because it is the same payer. Atomic link-if-absent ensures two simultaneous first purchases cannot produce two Customers.

**Billing identity lives on the Stripe Customer, not on the team.** A team document carries `name`, `createdBy`, `createdAt` and `members[]` — no legal name, address or VAT number. Those are collected at the first checkout through `tax_id_collection` and Stripe's address collection, and stored on the Customer, which becomes their system of record. The module does not extend the team schema.

**Invoices for all authenticated orders, receipts for guests.** `invoice_creation` and `tax_id_collection` are enabled when the payer is a team; the VAT number is entered once and reused on every later invoice. Guests get a receipt: with no Customer there is no invoice recipient, and enabling invoicing for them would make Stripe create exactly the single-use Customer this design avoids.

**⚠️ Team membership implies neither purchasing rights nor order visibility.** In `restheart-accounts` an invitation shares product access; it must not also hand over the company card. Only the team owner can purchase; members see nothing by default. Both are role-gated in the deployment's ACL, restrictive by default.

**The payer is named in the creation response and in the confirmation email.** A caller belonging to more than one team may not notice which is active; naming it makes a mischarge visible immediately rather than at the end of the month.

**`buyer_id` and `payer` are separate fields.** The actor may be deleted or leave the organisation; the order remains the payer's. Membership-based `readFilter` then stops showing them the team's orders.

```jsonc
{
  "buyer_id":    "andrea@softinstigate.com",   // who placed it — null for a guest
  "buyer_email": "andrea@softinstigate.com",   // where the confirmation goes
  "payer": {
    "type":               "team",              // team | guest
    "id":                 { "$oid": "64a1b2c3d4e5f6a7b8c9d0e1" },   // null for a guest
    "stripe_customer_id": "cus_SoftInstigate"
  }
}
```

### 7.7 The buyer's email

Used for the order confirmation and to pre-fill the Stripe Checkout page.

**Authenticated buyer** — read from the user document field named by:

```yaml
products:
  buyer-email-field: _id     # default; omit or null if users have none
```

`_id` is the default because `restheart-accounts` keys its users by email address. Left unset, `buyer_email` stays null until Stripe supplies one.

**Guest** — required in the request body, format-validated, `400` without it. Together with `items`, it is one of only two fields a caller may send.

```json
{ "email": "buyer@example.com", "items": [ { "productId": "SKU-1234", "quantity": 2 } ] }
```

**Stripe has the last word.** `customer_details.email` from the paid webhook replaces whatever the order started with: the customer can correct the address on the Checkout page, and the one Stripe recorded is the one that received the receipt.

Accepting an email from a guest does not weaken §7.1. It is a contact address, not an identity, and it is used for no authorization decision — guest access to an order is by `_id` + `secret` (§7.4), never by email, which would let anyone enumerate orders by guessing addresses.

**⚠️ Send nothing until paid.** A public endpoint that emails an address supplied in the request body is a spam relay: anyone could make the server send mail to any address, from your domain. Sending only on the paid webhook means an attacker would have to actually pay to send one message. The cost is that there is no "order received" email, only "order confirmed".

---

## 8. Configuration

One `stripeConfig`, two modes, independently enablable. **9.8.0 ships both.**

```yaml
stripeConfig:
  enabled: true

  # ── shared: one Stripe account, one webhook endpoint ──
  secret-key:     $(STRIPE_SECRET_KEY)
  webhook-secret: $(STRIPE_WEBHOOK_SECRET)

  subscriptions:
    enabled: true
    default-plan: free
    plans:
      free: { seats: { mode: capped, max: 1 } }
      pro:
        price-id-monthly: price_1AbC...
        seats: { mode: capped, max: 25 }
    success-url:       "https://app.example.com/billing?success=true"
    cancel-url:        "https://app.example.com/billing?canceled=true"
    portal-return-url: "https://app.example.com/billing"

  products:
    enabled: true
    init-enabled: true         # false to skip automatic init; use StripeProductsInitService on demand

    catalog-collection:      catalog
    orders-collection:       orders
    transactions-collection: transactions
    inventory-collection:    inventory      # omit to disable stock checks entirely

    default-currency: eur

    # user-document field holding the buyer's email; omit if users have none.
    # Guests always supply it in the request body. See §7.7.
    buyer-email-field: _id

    # Stripe-issued invoices for all authenticated orders; guests always get a receipt.
    invoice-team-orders: true
    collect-tax-id:      true

    success-url: "https://shop.example.com/done?session={CHECKOUT_SESSION_ID}"
    cancel-url:  "https://shop.example.com/cart"

    session-expires-minutes: 60

    max-line-items:        50
    max-quantity-per-line: 100

    # Stripe Tax. false => amounts are taken as final and tax is the deployment's problem.
    automatic-tax: true

    # Offered for carts containing a physical product.
    shipping-options:
      - display-name: Standard
        amount: 500
        delivery-estimate-days: { minimum: 3, maximum: 5 }
      - display-name: Express
        amount: 1500
        delivery-estimate-days: { minimum: 1, maximum: 2 }

    notifications:
      order-confirmed: { enabled: true }
      order-refunded:  { enabled: true }
```

Credentials and the database stay at the top level, declared once: the two modes cannot drift apart on them because there is only one copy. Each mode keeps its own `success-url` / `cancel-url` — a billing page and a shop cart are different destinations.

`automatic-tax: true` maps to Stripe's `automatic_tax`, in which case per-product `tax_code` is used. With `false`, `unit_amount` is the final price and tax is out of the module's hands. `shipping-options` maps to Stripe `shipping_options` and is offered only when the cart contains a physical line — both confirmed present in the pinned SDK.

### 8.1 The mode flags fix a bug that already exists

`StripeInitializer.validate()` returns `false` when `default-plan` is not a declared plan, and `init()` then returns early, **skipping everything after it** — including creating the `stripe_customer_id` unique index. RESTHeart logs initializer failures without aborting, so the deployment starts anyway.

So today, one typo in a plan id silently prevents index creation. That is a latent bug for subscription-only deployments right now, independent of this document.

Explicit modes make the fix structural: scope plan validation and `@subscription` registration to `subscriptions.enabled`, and move shared setup (indexes) out of that path. The products mode adds its own indexes — §5.3, §5.4 — to the same place.

### 8.2 Multi-tenancy

Product-mode overrides follow the existing convention: `override-stripe-products-catalog-collection`, `-orders-collection`, `-transactions-collection`, `-inventory-collection`, `-currency`, `-success-url`, `-cancel-url`.

`override-stripe-secret-key`, `override-stripe-webhook-secret` and `override-stripe-db` are **shared across both modes** — one tenant resolution feeds both, which is the point of keeping them at the top level.

**Disabling products per request.** A request parameter `rh-stripe-products-disabled` (value ignored, presence is the signal) makes the module skip all products-mode interceptors and webhook handling for that request. Default: not present = not blocked. This is the same override mechanism RESTHeart Cloud uses for other per-service controls: the platform attaches the parameter when the service has not enabled payments.

**On-demand initialization.** When `init-enabled: false`, the `stripeInitializer` skips products-mode setup (collections, indexes, schema). Instead, `StripeProductsInitService` — a `@Injectable` service — can be called programmatically to initialize a specific database. RESTHeart Cloud calls it when a service enables payments, passing the tenant database name. The service is idempotent (same rules as the initializer: create-if-absent, error on failed unique indexes).

### 8.3 ⚠️ A recurring catalog item collides with the subscriptions mode

`price_data` supports `setRecurring(...)`, so nothing structurally stops a catalog document describing a monthly box. It must be rejected when building line items.

Such an item creates a real Stripe Subscription. Stripe emits `customer.subscription.created`, the **subscriptions** mode picks it up, fails to resolve the ad-hoc price against `plans`, and — per the plan-attribution rule — keeps the previous plan and logs `unrecognised price id`. The customer is charged monthly, receives no plan, and the log fills with warnings that look like a misconfigured catalog.

The rule is behaving correctly; it was written assuming every subscription in the account is one the module created. With 9.8.0 shipping both modes, this collision is live from day one rather than hypothetical.

---

## 9. Endpoints: none

Orders are a MongoDB collection served by RESTHeart's existing API. There are no `/stripe/cart/*` or `/stripe/orders/*` services.

| Operation | Request |
|---|---|
| Create an order | `POST /orders` `{items:[{productId, quantity}]}` |
| List own orders | `GET /orders?filter={...}&sort=...&page=...` |
| Read one order | `GET /orders/{id}` (guest: `?secret=…`) |
| Catalog browse | `GET /catalog?filter={...}` |

Everything a read endpoint would have had to implement — filtering, sorting, pagination, projection, per-user scoping — is already there. The module contributes:

**1. `ordersCheckoutInterceptor`** — `REQUEST_AFTER_AUTH` on `POST /{orders-collection}`:

1. reject any top-level field other than `items`; validate item count and quantities
2. read `catalog` for the referenced products (one query, not one per line)
3. reject unpurchasable, unknown, recurring, mixed-currency; optionally check `inventory`
4. compute totals; create the Stripe Checkout Session
5. **replace** the request content with the full order document — `status: "pending_payment"`, `secret`, `stripe_session_id`, `checkout_url`, snapshot `line_items`, `expires_at`

**2. `ordersCheckoutResponseInterceptor`** — `RESPONSE`: a `POST` to a collection answers `201` with a `Location` header and no body, so this puts `{_id, checkout_url, secret}` in the response. Without it the client needs a second round trip to learn where to send the customer.

**3. Webhook handlers** (§6.1) — update the order and append to `transactions`.

**4. A `jsonSchema` on `orders`** describing the final document (§7.1).

**5. `StripeProductsInitService`** — `@Injectable` service for on-demand initialization. When `products.init-enabled: false`, the automatic initializer skips products-mode setup. RESTHeart Cloud calls this service programmatically when a service enables payments, passing the tenant database name. Idempotent: create-if-absent, error on failed unique indexes.

Writes other than the interceptor-mediated `POST` should be denied by ACL: a customer must not `PATCH` their own order to `status: "paid"`. Grant `POST` and `GET` only.

---

## 10. Installing the domain model

Before the products mode can serve one request there must be four collections, seven indexes, a JSON schema, and a permission set. Deciding which of those the module installs is a design question, and the answer is not the same for all of them.

### 10.1 What the initializer installs

There is precedent on both sides of this: `stripeInitializer` already creates the `stripe_customer_id` index, and `AccountsInitializer` creates collections and indexes — including a TTL one — guarded by `listCollectionNames`.

Extend `stripeInitializer`, which runs at `BEFORE_STARTUP` precisely so that everything exists before the first request is served:

| Artifact | Action |
|---|---|
| `catalog`, `orders`, `transactions`, `inventory` | `createCollection` if absent |
| `orders` indexes | unique `stripe_session_id`, unique `secret`, `(buyer_id, created_at)`, TTL on `expires_at` partial to `status: "pending_payment"` |
| `transactions` indexes | unique `stripe_event_id`, `order_id` |
| `orders` JSON schema | insert as `stripe-order-v1` into the schema store if absent |
| `orders` `jsonSchema` metadata | write the collection-properties document if absent |

Mechanics, verified in the codebase:

- the schema store is the `_schemas` collection (`ExchangeKeys._SCHEMAS`);
- collection metadata is a document in the `_properties` collection (`ExchangeKeys.META_COLLNAME`) with `_id = "_properties.<collName>"`, carrying `{"jsonSchema": {"schemaId": …}}`;
- there is **no install API** — `JsonSchemas` only validates. The initializer writes both documents through the `MongoClient` directly, which is new ground for this codebase.

`createCollection` and `createIndex` are both idempotent, so the whole routine is safe to run on every startup.

**⚠️ A failed unique index must be loud.** `AccountsInitializer` logs index failures as non-fatal warnings, which is right for its indexes — they are performance, not correctness. Here, `stripe_session_id` and `stripe_event_id` unique indexes **are the idempotency mechanism** (§6.3). Without them a redelivered webhook double-records a refund, and nothing else in the design catches it.

If either cannot be created — almost always because duplicates already exist — log at ERROR naming the collection and the duplicates, and treat it as a configuration failure the operator must resolve. Silently continuing means running a payment system whose idempotency guarantees are absent and untested.

### 10.2 ⚠️ What it must not install: the permissions

The initializer should create structure. It should **not** grant access.

The codebase already draws this line, though not explicitly. `AccountsInitializer` registers ACL programmatically — but a **veto**, a restriction that narrows what is reachable. `restheart-stripe` registers exactly one **allow**, for `/stripe/webhook`, and can justify it precisely: there is no possible authenticated caller on that path, and the endpoint secures itself by signature verification instead.

An orders permission is neither. It grants HTTP access to a data collection in the deployment's own database, and what is appropriate genuinely varies: is the shop public, are guests allowed, which roles may read, is `PATCH` denied. A library that silently grants access to a data collection takes away the property that makes an ACL reviewable — that you can read your permissions and know what is exposed.

So: ship the permission set as documented, copy-pasteable configuration (§7.4), not as code that runs.

**But close the loop with a warning.** The predictable consequence of not installing permissions is a deployment where products mode is enabled and `POST /orders` answers `403`, which looks like a module bug. `stripeInitializer` can detect that no permission grants access to the configured orders collection and say so at startup:

```
[stripe] products mode is enabled but no ACL permission grants access to `/orders` —
         POST /orders will answer 403 until one is configured (see documentation)
```

This is the same pattern already recommended for mode/plugin mismatches: the module states the problem at the one moment somebody is looking, without quietly fixing it on their behalf.

### 10.3 Install if absent; upgrades are a migration

Every artifact is installed only when missing, and never overwritten.

This matters most for the schema. A deployment will extend the order document — a fulfilment status, a warehouse reference, an internal note — and extending the document means extending the schema. Reinstalling the module's version on the next restart would silently invalidate every one of those documents on the next write.

**The schema `_id` is versioned:** `stripe-order-v1`, with the collection's `jsonSchema.schemaId` pointing at it. The version is there so a later module release can install `stripe-order-v2` alongside without colliding, and so a migration has something unambiguous to repoint the metadata at.

**Schema upgrades are not automatic.** A new module version installs its schema; it does not repoint an existing collection, because it cannot know what the deployment added to the old one. Moving to a new schema version is a migration the deployment performs deliberately.

Log every action at INFO, so a restart states exactly what it created and what it found already present.

### 10.4 Multi-tenancy

Everything here is created for the **statically configured** database only.

A deployment whose database varies per tenant (`override-stripe-db`) must create the collections, indexes and schema on every tenant database itself. This caveat already exists for the subscriptions mode's single index; the products mode makes it considerably larger, and the unique indexes it now covers are correctness-critical rather than merely useful.

**RESTHeart Cloud flow:** set `products.init-enabled: false` in the static config. When a service enables payments, call `StripeProductsInitService.init(dbName)` programmatically. The service creates collections, indexes and schema on the given database — idempotent, same rules as the automatic initializer.

---

## 11. Same module

Same module, additional disabled-by-default plugins. Two constraints agree:

- **The webhook endpoint.** Stripe delivers all events for an account to the configured URL. Two endpoints means two signing secrets and a new way to misconfigure a system whose failure mode is silent.
- **The unified configuration.** With `products` a sub-section of `stripeConfig`, a separate module would read a sub-block another module owns — inheriting its schema and lifecycle without owning either.

Under no circumstances a second webhook endpoint.

---

## 12. Effort and phasing

Both modes ship in 9.8.0, so this is one milestone rather than a follow-up. Sized in issues comparable to #667–#684.

**Core (~5 issues)**
`stripeConfig` restructured into `subscriptions` / `products` · catalog reader with the validation in §5.2 · `ordersCheckoutInterceptor` + response interceptor + `orders` JSON schema and indexes · `StripeEventHandler` SPI + dispatch refactor · order/transaction webhook handlers for `completed`, `async_*`, `expired`

**Completing it (~3 issues)**
Refunds and disputes into the ledger · Stripe Tax and shipping options · order-confirmation and refund notifications · optional stock check

**Multi-tenancy (~1 issue)**
`StripeProductsInitService` for on-demand initialization · `rh-stripe-products-disabled` request parameter · `init-enabled` config flag

Smaller than the subscriptions mode was, because the read side is not written at all.

---

## 13. Decisions settled

1. **9.8.0 ships both modes** — the `stripeConfig` restructure is part of its design, not a migration.
2. **Tax** — support both; `automatic_tax` is what we use.
3. **Shipping** — Stripe `shipping_options`.
4. **Order id** — `_id` is an ObjectId; `stripe_session_id` is stored and client-visible. No sequential order number.
5. **No mixed carts** — a checkout is products or a subscription, never both.
6. **No "order received" email.** Confirmation is sent on the paid webhook only, which keeps the public endpoint from being usable as a spam relay (§7.7).
7. **Guest order access does not expire.** `_id` + `secret` stays valid indefinitely: the customer keeps a working receipt link, and the secret is high-entropy and single-order.
8. **Schema installation is install-if-absent, with a versioned `_id`** (`stripe-order-v1`). Upgrades are a deliberate migration, never automatic (§10.3).
9. **`catalog` write authorization is the deployment's ACL decision.** Documented default: admin-only write, public read.

---

## 14. Recommendations

1. Payments only; the four schemas are fixed and not pluggable. (§1)
2. No custom endpoints — orders are a collection, logic in interceptors, authorization in ACL. (§9)
3. Catalog stays in MongoDB; ad-hoc `price_data`, `stripe_price_id` as a per-product escape hatch. (§5.2)
4. **Never accept a price from the client**; the interceptor builds the document from scratch and rejects unexpected fields. (§7.1)
5. Know that the JSON schema validates the *final* document, not the client body. (§7.1)
6. `buyer_id` stamped via `mergeRequest`, team-scoped orders via `readFilter`. (§7.4)
7. Guest access by `_id` + `secret` through `readFilter: {"secret": "@qparams['secret']"}`. (§7.4)
8. Guest checkout via ACL, not a config flag. (§7.5)
9. TTL index on `expires_at` partial to `pending_payment` — abandoned carts clean themselves up. (§7.5)
10. `buyer_email` from `buyer-email-field` when authenticated, from the body when a guest, and overwritten by Stripe's once paid. Notify only after payment. (§7.7)
11. One Stripe Customer **per team**, shared with subscriptions; guests get none. A user in N teams has N billing profiles. (§7.6)
12. Handle `async_payment_*`; only `payment_status == "paid"` means paid. (§6.1)
13. Idempotency: monotonic status transitions, unique `stripe_event_id` on transactions. (§6.3)
14. Snapshot line items onto the order. (§7.2)
15. Scope plan validation to `subscriptions.enabled`; fixes an existing latent bug. (§8.1)
16. Reject recurring catalog items — live from day one now that both modes ship together. (§8.3)
17. Deny `PATCH`/`PUT` on orders by ACL; only `POST` and `GET`. (§9)
18. `stripeInitializer` installs collections, indexes and the JSON schema (`stripe-order-v1`) — install-if-absent, never overwriting; upgrades are a migration. (§10.1, §10.3)
19. **It does not install permissions.** Structure is the module's; grants stay visible in the deployment's own ACL. Warn at startup when nothing grants access to the orders collection. (§10.2)
20. A failed unique index on `stripe_session_id` or `stripe_event_id` is an ERROR, not a warning — those indexes *are* the idempotency mechanism. (§10.1)
21. `invoice_creation` + `tax_id_collection` for all authenticated orders; guests get a receipt. (§7.6)
22. Only team owners can purchase; members see nothing by default — both role-gated, restrictive by default. (§7.6)
23. Name the payer in the creation response and the confirmation, so a wrong active team is caught immediately. (§7.6)
