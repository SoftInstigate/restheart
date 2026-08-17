@helper
Feature: seed an order document directly, bypassing ordersCheckoutInterceptor

  # Called with { orderId, sessionId }. Writing the order as admin is what lets the whole
  # lifecycle suite run without a Stripe key: the webhook handler only needs the document
  # to exist and to carry stripe_session_id.
  #
  # orderId MUST be a 24-char hex string: RESTHeart stores it as an ObjectId, and
  # OrderEventHandler reads it back with result.getObjectId("_id") when writing the ledger.
  #
  # Tagged @helper so RunnerIT does not execute it as a feature of its own.

  Scenario: put
    * url baseUrl
    * def basic =
    """
    function(creds) {
      var temp = creds.username + ':' + creds.password;
      var Base64 = Java.type('java.util.Base64');
      return 'Basic ' + Base64.getEncoder().encodeToString(temp.toString().getBytes());
    }
    """
    * def adminAuth = basic({ username: 'admin', password: 'secret' })

    * def order =
    """
    {
      "stripe_session_id": "#(sessionId)",
      "stripe_payment_intent": null,
      "secret": "#('seed-secret-' + sessionId)",
      "buyer_id": "buyer@example.com",
      "buyer_email": "buyer@example.com",
      "payer": { "type": "guest", "id": null, "stripe_customer_id": null },
      "status": "pending_payment",
      "requires_shipping": false,
      "line_items": [
        { "product_id": "SKU-WIDGET", "type": "digital", "name": "Blue Widget",
          "unit_amount": 2500, "quantity": 2, "subtotal": 5000, "tax_code": null }
      ],
      "currency": "eur",
      "amount_subtotal": 5000,
      "amount_tax": 0,
      "amount_shipping": 0,
      "amount_total": 5000,
      "amount_refunded": 0,
      "shipping_address": null
    }
    """

    # No delete here: the calling feature's Background clears every seeded order once, which
    # also catches documents left by an earlier run under a different _id.

    Given path '/restheart-test/orders/' + orderId
    And param wm = 'upsert'
    And header Authorization = adminAuth
    And request order
    When method PUT
    * match [200, 201] contains responseStatus
