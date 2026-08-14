Feature: POST /stripe/webhook — plan attribution and staleness guard

Background:
    * url 'http://localhost:8080'
    * def webhookSecret = 'whsec_test_secret_for_karate_tests'

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

    Given path '/restheart-test'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201] contains responseStatus

    Given path '/teams'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201] contains responseStatus

Scenario: an unrecognised price id keeps the previous plan instead of falling back to the default
    * def teamId = '507f191e810c19729de860eb'
    * def customerId = 'cus_test_plan_attr_1'

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method DELETE
    * match [204, 404] contains responseStatus

    * def team =
    """
    {
      stripe_customer_id: '#(customerId)',
      subscription: {
        plan: 'gold',
        price_id: 'price_test_gold_monthly',
        status: 'active',
        stripe_subscription_id: 'sub_prior',
        seats: 1,
        cancel_at_period_end: false
      },
      members: [ { userId: 'u1', role: 'owner', licensed: true } ]
    }
    """
    Given path '/teams/' + teamId
    # PUT to a document defaults to write-mode 'update' (404 if absent) — 'upsert' is
    # required to create it fresh. See MongoRequest#defaultWriteMode.
    And param wm = 'upsert'
    And header Authorization = adminAuth
    And request team
    When method PUT
    * match [200, 201] contains responseStatus

    * def event =
    """
    {
      id: 'evt_unknown_price',
      object: 'event',
      type: 'customer.subscription.updated',
      created: 1700000100,
      data: {
        object: {
          id: 'sub_prior',
          object: 'subscription',
          customer: '#(customerId)',
          status: 'active',
          cancel_at_period_end: false,
          items: {
            object: 'list',
            data: [
              {
                id: 'si_test_2',
                object: 'subscription_item',
                quantity: 1,
                current_period_end: 1702592400,
                price: { id: 'price_totally_unrecognised', object: 'price' }
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
    # plan must stay 'gold' — an unrecognised price must never fall back to the default plan
    And match response.subscription.plan == 'gold'
    And match response.subscription.price_id == 'price_totally_unrecognised'

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method DELETE
    Then status 204

Scenario: an out-of-order subscription.updated delivered after subscription.deleted is skipped as stale, and the webhook still answers 200
    * def teamId = '507f191e810c19729de860ec'
    * def customerId = 'cus_test_staleness_1'

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method DELETE
    * match [204, 404] contains responseStatus

    * def team = { stripe_customer_id: '#(customerId)', members: [ { userId: 'u1', role: 'owner', licensed: true } ] }
    Given path '/teams/' + teamId
    And param wm = 'upsert'
    And header Authorization = adminAuth
    And request team
    When method PUT
    * match [200, 201] contains responseStatus

    # 1. subscription.deleted, event created at T=1700000300 — cancels the subscription.
    * def deletedEvent =
    """
    {
      id: 'evt_deleted_1',
      object: 'event',
      type: 'customer.subscription.deleted',
      created: 1700000300,
      data: {
        object: {
          id: 'sub_stale_1',
          object: 'subscription',
          customer: '#(customerId)',
          status: 'canceled'
        }
      }
    }
    """
    * def deletedPayload = karate.toString(deletedEvent)
    * def deletedSig = sign(webhookSecret, deletedPayload)

    Given path '/stripe/webhook'
    And header Stripe-Signature = deletedSig
    And request deletedPayload
    When method POST
    Then status 200

    # 2. A redelivered subscription.updated for the SAME subscription, but with an event
    #    timestamp (T=1700000100) OLDER than the one already applied — must be skipped.
    * def staleEvent =
    """
    {
      id: 'evt_stale_update_1',
      object: 'event',
      type: 'customer.subscription.updated',
      created: 1700000100,
      data: {
        object: {
          id: 'sub_stale_1',
          object: 'subscription',
          customer: '#(customerId)',
          status: 'active',
          cancel_at_period_end: false,
          items: {
            object: 'list',
            data: [
              {
                id: 'si_stale_1',
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
    * def stalePayload = karate.toString(staleEvent)
    * def staleSig = sign(webhookSecret, stalePayload)

    Given path '/stripe/webhook'
    And header Stripe-Signature = staleSig
    And request stalePayload
    When method POST
    # A skipped-as-stale write is not an error — Stripe must not retry it.
    Then status 200

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method GET
    Then status 200
    # the entity must stay canceled — the stale 'active' update must not have applied
    And match response.subscription.status == 'canceled'

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method DELETE
    Then status 204
