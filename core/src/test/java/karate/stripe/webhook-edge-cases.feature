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
      api_version: '2026-07-29.dahlia',
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
      api_version: '2026-07-29.dahlia',
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
      api_version: '2026-07-29.dahlia',
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

Scenario: customer.subscription.created links a brand new subscription to its team
    * def teamId = '507f191e810c19729de860ed'
    * def customerId = 'cus_test_sub_created_1'

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

    * def event =
    """
    {
      id: 'evt_sub_created_1',
      object: 'event',
      api_version: '2026-07-29.dahlia',
      type: 'customer.subscription.created',
      created: 1700000900,
      data: {
        object: {
          id: 'sub_created_1',
          object: 'subscription',
          customer: '#(customerId)',
          status: 'active',
          cancel_at_period_end: false,
          items: {
            object: 'list',
            data: [
              {
                id: 'si_created_1',
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
    And match response.subscription.stripe_subscription_id == 'sub_created_1'

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method DELETE
    Then status 204

Scenario: customer.subscription.trial_will_end records the notification timestamp
    * def teamId = '507f191e810c19729de860ee'
    * def customerId = 'cus_test_trial_end_1'

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
        status: 'trialing',
        stripe_subscription_id: 'sub_trial_1',
        seats: 1,
        cancel_at_period_end: false
      },
      members: [ { userId: 'u1', role: 'owner', licensed: true } ]
    }
    """
    Given path '/teams/' + teamId
    And param wm = 'upsert'
    And header Authorization = adminAuth
    And request team
    When method PUT
    * match [200, 201] contains responseStatus

    * def event =
    """
    {
      id: 'evt_trial_end_1',
      object: 'event',
      api_version: '2026-07-29.dahlia',
      type: 'customer.subscription.trial_will_end',
      created: 1700001000,
      data: {
        object: {
          id: 'sub_trial_1',
          object: 'subscription',
          customer: '#(customerId)',
          status: 'trialing'
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
    And match response.subscription.trial_will_end_notified_at == '#present'

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method DELETE
    Then status 204

Scenario: invoice.payment_succeeded reactivates a past_due subscription
    * def teamId = '507f191e810c19729de860ef'
    * def customerId = 'cus_test_invoice_ok_1'

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
        status: 'past_due',
        stripe_subscription_id: 'sub_invoice_ok_1',
        seats: 1,
        cancel_at_period_end: false
      },
      members: [ { userId: 'u1', role: 'owner', licensed: true } ]
    }
    """
    Given path '/teams/' + teamId
    And param wm = 'upsert'
    And header Authorization = adminAuth
    And request team
    When method PUT
    * match [200, 201] contains responseStatus

    * def event =
    """
    {
      id: 'evt_invoice_ok_1',
      object: 'event',
      api_version: '2026-07-29.dahlia',
      type: 'invoice.payment_succeeded',
      created: 1700001100,
      data: {
        object: {
          id: 'in_test_ok_1',
          object: 'invoice',
          customer: '#(customerId)',
          parent: {
            type: 'subscription_details',
            subscription_details: { subscription: 'sub_invoice_ok_1' }
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
    And match response.subscription.status == 'active'

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method DELETE
    Then status 204

Scenario: invoice.payment_failed marks an active subscription past_due
    * def teamId = '507f191e810c19729de860f0'
    * def customerId = 'cus_test_invoice_fail_1'

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
        status: 'active',
        stripe_subscription_id: 'sub_invoice_fail_1',
        seats: 1,
        cancel_at_period_end: false
      },
      members: [ { userId: 'u1', role: 'owner', licensed: true } ]
    }
    """
    Given path '/teams/' + teamId
    And param wm = 'upsert'
    And header Authorization = adminAuth
    And request team
    When method PUT
    * match [200, 201] contains responseStatus

    * def event =
    """
    {
      id: 'evt_invoice_fail_1',
      object: 'event',
      api_version: '2026-07-29.dahlia',
      type: 'invoice.payment_failed',
      created: 1700001200,
      data: {
        object: {
          id: 'in_test_fail_1',
          object: 'invoice',
          customer: '#(customerId)',
          parent: {
            type: 'subscription_details',
            subscription_details: { subscription: 'sub_invoice_fail_1' }
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
    And match response.subscription.status == 'past_due'

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method DELETE
    Then status 204

Scenario: product.updated and price.updated are accepted and just invalidate the catalog cache
    * def productEvent =
    """
    { id: 'evt_product_updated_1', object: 'event', api_version: '2026-07-29.dahlia',
      type: 'product.updated', created: 1700001300,
      data: { object: { id: 'prod_test_1', object: 'product' } } }
    """
    * def p1 = karate.toString(productEvent)
    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, p1)
    And request p1
    When method POST
    Then status 200

    * def priceEvent =
    """
    { id: 'evt_price_updated_1', object: 'event', api_version: '2026-07-29.dahlia',
      type: 'price.updated', created: 1700001400,
      data: { object: { id: 'price_test_1', object: 'price' } } }
    """
    * def p2 = karate.toString(priceEvent)
    Given path '/stripe/webhook'
    And header Stripe-Signature = sign(webhookSecret, p2)
    And request p2
    When method POST
    Then status 200
