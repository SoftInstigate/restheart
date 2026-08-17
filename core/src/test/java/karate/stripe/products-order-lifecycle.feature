Feature: products mode — order state machine and money ledger

# The order is seeded directly as admin, so no Stripe call is needed anywhere here: the
# webhook handler finds the order by stripe_session_id and the signatures are computed
# locally against the configured whsec_. This is the suite that protects the ledger.

Background:
    * url baseUrl
    * configure followRedirects = false
    * def webhookSecret = 'whsec_test_secret_for_karate_tests'

    * def basic =
    """
    function(creds) {
      var temp = creds.username + ':' + creds.password;
      var Base64 = Java.type('java.util.Base64');
      return 'Basic ' + Base64.getEncoder().encodeToString(temp.toString().getBytes());
    }
    """
    * def adminAuth = basic({ username: 'admin', password: 'secret' })

    # Stripe's scheme: HMAC-SHA256 over "<timestamp>.<raw body>", header "t=...,v1=<hex>"
    * def sign =
    """
    function(secret, payload) {
      var timestamp = Math.floor(new Date().getTime() / 1000);
      var Mac = Java.type('javax.crypto.Mac');
      var SecretKeySpec = Java.type('javax.crypto.spec.SecretKeySpec');
      var mac = Mac.getInstance('HmacSHA256');
      mac.init(new SecretKeySpec(secret.getBytes(), 'HmacSHA256'));
      var bytes = mac.doFinal((timestamp + '.' + payload).getBytes());
      var hex = '';
      for (var i = 0; i < bytes.length; i++) {
        var s = (bytes[i] & 0xff).toString(16);
        if (s.length === 1) s = '0' + s;
        hex += s;
      }
      return 't=' + timestamp + ',v1=' + hex;
    }
    """

    # A deterministic 24-hex ObjectId per session id, so a re-run overwrites the same
    # document instead of accumulating. Hex of the MD5 of the session id, first 24 chars.
    * def oidFor =
    """
    function(sessionId) {
      var MessageDigest = Java.type('java.security.MessageDigest');
      var digest = MessageDigest.getInstance('MD5').digest(sessionId.getBytes());
      var hex = '';
      for (var i = 0; i < digest.length; i++) {
        var s = (digest[i] & 0xff).toString(16);
        if (s.length === 1) s = '0' + s;
        hex += s;
      }
      return hex.substring(0, 24);
    }
    """

    # Seeds a pending_payment order. orderId must be 24 hex chars so RESTHeart stores it
    # as an ObjectId — OrderEventHandler reads it back with getObjectId("_id").
    * def seedOrder =
    """
    function(orderId, sessionId) {
      karate.call('classpath:karate/stripe/helpers/put-order.feature',
                  { orderId: orderId, sessionId: sessionId });
    }
    """

    Given path '/restheart-test'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201] contains responseStatus

    Given path '/restheart-test/orders'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201] contains responseStatus

    Given path '/restheart-test/transactions'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201] contains responseStatus

    # Every seeded order carries this buyer_id, and nothing else in the suite does. Clearing
    # them here makes the run idempotent: orders has unique indexes on stripe_session_id and
    # on secret, so any document left behind by an earlier (or half-broken) run would collide
    # on insert and fail every scenario after the first.
    Given path '/restheart-test/orders/*'
    And param filter = '{"buyer_id":"buyer@example.com"}'
    And header Authorization = adminAuth
    When method DELETE
    * match [200, 204, 404] contains responseStatus

# ── the async-payment trap ───────────────────────────────────────────────────

Scenario: checkout.session.completed with payment_status unpaid must NOT mark the order paid
    # With SEPA, Bacs, BLIK and bank transfers Stripe sends completed while the money is
    # still in flight. Treating that as paid ships goods for payments that may still fail,
    # and card-only testing never surfaces it.
    * def sid = 'cs_test_async_unpaid'
    * def oid = oidFor(sid)
    * eval seedOrder(oid, sid)

    * def event =
    """
    {
      id: 'evt_async_unpaid', object: 'event', api_version: '2026-07-29.dahlia',
      type: 'checkout.session.completed', created: 1700000000,
      data: { object: { id: '#(sid)', object: 'checkout_session', payment_status: 'unpaid',
                        amount_total: 5000, currency: 'eur' } }
    }
    """
    * def payload = karate.toString(event)
    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, payload)
    And request payload
    When method POST
    Then status 200

    Given path '/restheart-test/orders/' + oid
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.status == 'pending_payment'
    And match response.paid_at == '#notpresent'

Scenario: the same session paid later via async_payment_succeeded becomes paid
    * def sid = 'cs_test_async_ok'
    * def oid = oidFor(sid)
    * eval seedOrder(oid, sid)

    * def event =
    """
    {
      id: 'evt_async_ok', object: 'event', api_version: '2026-07-29.dahlia',
      type: 'checkout.session.async_payment_succeeded', created: 1700000100,
      data: { object: { id: '#(sid)', object: 'checkout_session', payment_status: 'paid',
                        payment_intent: 'pi_async_ok', amount_total: 5000, currency: 'eur' } }
    }
    """
    * def payload = karate.toString(event)
    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, payload)
    And request payload
    When method POST
    Then status 200

    Given path '/restheart-test/orders/' + oid
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.status == 'paid'

Scenario: async_payment_failed marks the order failed
    * def sid = 'cs_test_async_fail'
    * def oid = oidFor(sid)
    * eval seedOrder(oid, sid)

    * def event =
    """
    {
      id: 'evt_async_fail', object: 'event', api_version: '2026-07-29.dahlia',
      type: 'checkout.session.async_payment_failed', created: 1700000200,
      data: { object: { id: '#(sid)', object: 'checkout_session', payment_status: 'unpaid' } }
    }
    """
    * def payload = karate.toString(event)
    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, payload)
    And request payload
    When method POST
    Then status 200

    Given path '/restheart-test/orders/' + oid
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.status == 'failed'

# ── monotonic transitions ────────────────────────────────────────────────────

Scenario: a paid order is never moved back to expired
    # Stripe can deliver session.expired after the payment succeeded; the guard is that
    # every transition asserts the current status.
    * def sid = 'cs_test_no_regress'
    * def oid = oidFor(sid)
    * eval seedOrder(oid, sid)

    * def paidEvent =
    """
    {
      id: 'evt_paid_first', object: 'event', api_version: '2026-07-29.dahlia',
      type: 'checkout.session.completed', created: 1700000300,
      data: { object: { id: '#(sid)', object: 'checkout_session', payment_status: 'paid',
                        payment_intent: 'pi_no_regress', amount_total: 5000, currency: 'eur' } }
    }
    """
    * def p1 = karate.toString(paidEvent)
    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, p1)
    And request p1
    When method POST
    Then status 200

    * def expiredEvent =
    """
    {
      id: 'evt_expired_after', object: 'event', api_version: '2026-07-29.dahlia',
      type: 'checkout.session.expired', created: 1700000400,
      data: { object: { id: '#(sid)', object: 'checkout_session' } }
    }
    """
    * def p2 = karate.toString(expiredEvent)
    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, p2)
    And request p2
    When method POST
    # a superseded event is not an error — Stripe must stop retrying it
    Then status 200

    Given path '/restheart-test/orders/' + oid
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.status == 'paid'

Scenario: an abandoned session expires
    * def sid = 'cs_test_expired'
    * def oid = oidFor(sid)
    * eval seedOrder(oid, sid)

    * def event =
    """
    {
      id: 'evt_expired_only', object: 'event', api_version: '2026-07-29.dahlia',
      type: 'checkout.session.expired', created: 1700000500,
      data: { object: { id: '#(sid)', object: 'checkout_session' } }
    }
    """
    * def payload = karate.toString(event)
    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, payload)
    And request payload
    When method POST
    Then status 200

    Given path '/restheart-test/orders/' + oid
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.status == 'expired'

# ── ledger idempotency ───────────────────────────────────────────────────────

Scenario: a redelivered paid event does not record the payment twice
    # Stripe retries. The unique index on transactions.stripe_event_id is what makes a
    # replay a no-op instead of doubling the money recorded.
    * def sid = 'cs_test_replay'
    * def oid = oidFor(sid)
    * eval seedOrder(oid, sid)

    * def event =
    """
    {
      id: 'evt_replayed_once', object: 'event', api_version: '2026-07-29.dahlia',
      type: 'checkout.session.completed', created: 1700000600,
      data: { object: { id: '#(sid)', object: 'checkout_session', payment_status: 'paid',
                        payment_intent: 'pi_replay', amount_total: 5000, currency: 'eur' } }
    }
    """
    * def payload = karate.toString(event)

    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, payload)
    And request payload
    When method POST
    Then status 200

    # exact same event id, delivered again
    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, payload)
    And request payload
    When method POST
    Then status 200

    Given path '/restheart-test/transactions'
    And header Authorization = adminAuth
    And param filter = '{"stripe_event_id":"evt_replayed_once"}'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length == 1

# ── refunds ──────────────────────────────────────────────────────────────────

Scenario: a refund is appended to the ledger and reflected on the order
    * def sid = 'cs_test_refund'
    * def oid = oidFor(sid)
    * eval seedOrder(oid, sid)

    * def paidEvent =
    """
    {
      id: 'evt_refund_paid', object: 'event', api_version: '2026-07-29.dahlia',
      type: 'checkout.session.completed', created: 1700000700,
      data: { object: { id: '#(sid)', object: 'checkout_session', payment_status: 'paid',
                        payment_intent: 'pi_refund', amount_total: 5000, currency: 'eur' } }
    }
    """
    * def p1 = karate.toString(paidEvent)
    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, p1)
    And request p1
    When method POST
    Then status 200

    # partial refund of 20.00 on a 50.00 order
    * def refundEvent =
    """
    {
      id: 'evt_refund_partial', object: 'event', api_version: '2026-07-29.dahlia',
      type: 'charge.refunded', created: 1700000800,
      data: { object: { id: 'ch_refund', object: 'charge', payment_intent: 'pi_refund',
                        amount: 5000, amount_refunded: 2000, currency: 'eur' } }
    }
    """
    * def p2 = karate.toString(refundEvent)
    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, p2)
    And request p2
    When method POST
    Then status 200

    Given path '/restheart-test/orders/' + oid
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.amount_refunded == 2000

    # the ledger must balance: payment + refund == what the buyer is left having paid
    Given path '/restheart-test/transactions'
    And header Authorization = adminAuth
    And param filter = '{"order_id":{"$oid":"' + oid + '"}}'
    And param sort = '{"occurred_at":1}'
    And param rep = 's'
    When method GET
    Then status 200
    * def total = 0
    * eval for (var i = 0; i < response.length; i++) total += response[i].amount
    * assert total == 3000
