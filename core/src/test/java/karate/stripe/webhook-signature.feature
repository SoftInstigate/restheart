Feature: POST /stripe/webhook signature verification and state update

# stripeConfig.webhook-secret is set in conf-overrides.yml to a fixed test value —
# see /stripeConfig/webhook-secret. No real Stripe account is involved: the signature
# is just an HMAC-SHA256 the test computes itself with the same shared secret.

Background:
    * url 'http://localhost:8080'
    * def webhookSecret = 'whsec_test_secret_for_karate_tests'
    * def teamId = '507f191e810c19729de860ea'
    * def customerId = 'cus_test_webhook_1'

    * def basic =
    """
    function(creds) {
      var temp = creds.username + ':' + creds.password;
      var Base64 = Java.type('java.util.Base64');
      var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
      return 'Basic ' + encoded;
    }
    """
    * def adminAuth = basic({ username: 'admin', password: 'secret' })

    # Ensure db/collection exist — tolerant of either status since the accounts test
    # suite may (or may not, depending on run order) have already created them.
    Given path '/restheart-test'
    And header Authorization = adminAuth
    When method PUT
    Then match [200, 201] contains responseStatus

    Given path '/teams'
    And header Authorization = adminAuth
    When method PUT
    Then match [200, 201] contains responseStatus

    # Stripe's own signing scheme: sign "<timestamp>.<raw body>" with HMAC-SHA256,
    # header value is "t=<timestamp>,v1=<hex hmac>". See com.stripe.net.Webhook.
    * def sign =
    """
    function(secret, payload) {
      var timestamp = Math.floor(new Date().getTime() / 1000);
      var signedPayload = timestamp + '.' + payload;
      var SecretKeySpec = Java.type('javax.crypto.spec.SecretKeySpec');
      var Mac = Java.type('javax.crypto.Mac');
      var mac = Mac.getInstance('HmacSHA256');
      mac.init(new SecretKeySpec(secret.getBytes(), 'HmacSHA256'));
      var hmacBytes = mac.doFinal(signedPayload.getBytes());
      var hex = '';
      for (var i = 0; i < hmacBytes.length; i++) {
        var b = hmacBytes[i] & 0xff;
        var s = b.toString(16);
        if (s.length === 1) s = '0' + s;
        hex += s;
      }
      return 't=' + timestamp + ',v1=' + hex;
    }
    """

Scenario: request without a Stripe-Signature header is rejected
    Given path '/stripe/webhook'
    And request { id: 'evt_1', type: 'ping' }
    When method POST
    Then status 400

Scenario: request with an invalid signature is rejected
    * def payload = karate.toString({ id: 'evt_1', type: 'ping' })
    Given path '/stripe/webhook'
    And header Stripe-Signature = 't=1,v1=deadbeef'
    And request payload
    When method POST
    Then status 400

Scenario: a verified event of an unhandled type is accepted and ignored
    * def payload = karate.toString({ id: 'evt_ping', object: 'event', type: 'ping', created: 1700000000, data: { object: {} } })
    * def sig = sign(webhookSecret, payload)
    Given path '/stripe/webhook'
    And header Stripe-Signature = sig
    And request payload
    When method POST
    Then status 200

Scenario: customer.subscription.updated for a linked team updates its persisted subscription state
    # Clean slate: remove any leftover team doc from a previous run.
    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method DELETE
    * match [204, 404] contains responseStatus

    * def team = { stripe_customer_id: '#(customerId)', members: [ { userId: 'u1', role: 'owner', licensed: false } ] }
    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    And request team
    When method PUT
    * match [200, 201] contains responseStatus

    * def event =
    """
    {
      id: 'evt_sub_updated_1',
      object: 'event',
      type: 'customer.subscription.updated',
      created: 1700000000,
      data: {
        object: {
          id: 'sub_test_1',
          object: 'subscription',
          customer: '#(customerId)',
          status: 'active',
          cancel_at_period_end: false,
          items: {
            object: 'list',
            data: [
              {
                id: 'si_test_1',
                object: 'subscription_item',
                quantity: 1,
                current_period_end: 1702592400,
                price: { id: 'price_test_gold_monthly', object: 'price' }
              }
            ]
          }
        }
      }
    }
    """
    * def payload = karate.toString(event)
    * def sig = sign(webhookSecret, payload)

    Given path '/stripe/webhook'
    And header Stripe-Signature = sig
    And request payload
    When method POST
    Then status 200

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.subscription.plan == 'gold'
    And match response.subscription.status == 'active'
    And match response.subscription.price_id == 'price_test_gold_monthly'
    And match response.subscription.seats == 1

    # Cleanup.
    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method DELETE
    Then status 204
