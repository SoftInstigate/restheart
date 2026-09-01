Feature: products mode — the client can never influence what it is charged

# Every scenario here is rejected by ordersCheckoutInterceptor BEFORE it calls Stripe,
# so none of them need a Stripe key, a network, or a mock. That is deliberate: these are
# the assertions that protect money, and they must never be skipped for lack of a secret.
#
# The interceptor runs at REQUEST_AFTER_AUTH on POST /{orders-collection} and replaces the
# request content with a server-built order document. A client that manages to get a price,
# a total or a status through this is a vulnerability, not a bug.

Background:
    * url baseUrl
    * configure followRedirects = false

    * def basic =
    """
    function(creds) {
      var temp = creds.username + ':' + creds.password;
      var Base64 = Java.type('java.util.Base64');
      return 'Basic ' + Base64.getEncoder().encodeToString(temp.toString().getBytes());
    }
    """
    * def adminAuth = basic({ username: 'admin', password: 'secret' })

    * def ownerSetup = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def authHeader = 'Bearer ' + ownerSetup.ownerJwt

    # ── catalog fixture ──────────────────────────────────────────────────────
    Given path '/restheart-test'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201] contains responseStatus

    Given path '/restheart-test/catalog'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201] contains responseStatus

    Given path '/restheart-test/orders'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201] contains responseStatus

    # a normal, purchasable digital product at €25.00
    Given path '/restheart-test/catalog/SKU-WIDGET'
    And param wm = 'upsert'
    And header Authorization = adminAuth
    And request
      """
      {
        "type": "digital",
        "name": "Blue Widget",
        "unit_amount": 2500,
        "currency": "eur",
        "purchasable": true
      }
      """
    When method PUT
    * match [200, 201] contains responseStatus

    # deliberately withdrawn from sale
    Given path '/restheart-test/catalog/SKU-RETIRED'
    And param wm = 'upsert'
    And header Authorization = adminAuth
    And request
      """
      { "type": "digital", "name": "Retired", "unit_amount": 500, "currency": "eur", "purchasable": false }
      """
    When method PUT
    * match [200, 201] contains responseStatus

    # priced in a different currency — a cart may not mix currencies
    Given path '/restheart-test/catalog/SKU-USD'
    And param wm = 'upsert'
    And header Authorization = adminAuth
    And request
      """
      { "type": "digital", "name": "Dollar Item", "unit_amount": 900, "currency": "usd", "purchasable": true }
      """
    When method PUT
    * match [200, 201] contains responseStatus

# ── the core guarantee ───────────────────────────────────────────────────────

Scenario: a client-supplied price is rejected, not silently ignored
    # Silently dropping the field would hide a frontend bug until the totals disagree.
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { productId: 'SKU-WIDGET', quantity: 1, unit_amount: 1 } ], unit_amount: 1 }
    When method POST
    Then status 400

Scenario: a client-supplied total is rejected
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { productId: 'SKU-WIDGET', quantity: 1 } ], amount_total: 1 }
    When method POST
    Then status 400

Scenario: a client-supplied status is rejected
    # Otherwise a caller could post an order already marked paid.
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { productId: 'SKU-WIDGET', quantity: 1 } ], status: 'paid' }
    When method POST
    Then status 400

Scenario: a client-supplied secret is rejected
    # The order secret is the guest access credential; it must be server-generated only.
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { productId: 'SKU-WIDGET', quantity: 1 } ], secret: 'attacker-chosen' }
    When method POST
    Then status 400

# ── quantity bounds ──────────────────────────────────────────────────────────

Scenario Outline: quantity <quantity> is rejected
    # A negative quantity against a permissive account is a refund-generating machine.
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { productId: 'SKU-WIDGET', quantity: <quantity> } ] }
    When method POST
    Then status 400

    Examples:
      | quantity |
      | 0        |
      | -1       |
      | -1000    |
      | 11       |

Scenario: a non-numeric quantity is rejected
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { productId: 'SKU-WIDGET', quantity: '1' } ] }
    When method POST
    Then status 400

# ── cart shape ───────────────────────────────────────────────────────────────

Scenario: an empty cart is rejected
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [] }
    When method POST
    Then status 400

Scenario: a cart with more lines than max-line-items is rejected
    # max-line-items is 5 in conf-overrides.yml
    * def many = [{productId:'SKU-WIDGET',quantity:1},{productId:'SKU-WIDGET',quantity:1},{productId:'SKU-WIDGET',quantity:1},{productId:'SKU-WIDGET',quantity:1},{productId:'SKU-WIDGET',quantity:1},{productId:'SKU-WIDGET',quantity:1}]
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: '#(many)' }
    When method POST
    Then status 400

Scenario: a missing productId is rejected
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { quantity: 1 } ] }
    When method POST
    Then status 400

# ── catalog integrity ────────────────────────────────────────────────────────

Scenario: an unknown product is rejected
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { productId: 'SKU-DOES-NOT-EXIST', quantity: 1 } ] }
    When method POST
    Then status 400

Scenario: a product marked unpurchasable cannot be bought
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { productId: 'SKU-RETIRED', quantity: 1 } ] }
    When method POST
    Then status 409

Scenario: a cart mixing currencies is rejected
    # A Checkout session has exactly one currency; failing here beats failing at Stripe.
    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { productId: 'SKU-WIDGET', quantity: 1 }, { productId: 'SKU-USD', quantity: 1 } ] }
    When method POST
    Then status 400

Scenario: a catalog item with a non-integer unit_amount is refused rather than rounded
    # 25.00 written by someone thinking in euros would otherwise charge 25 cents.
    Given path '/restheart-test/catalog/SKU-BROKEN'
    And param wm = 'upsert'
    And header Authorization = adminAuth
    And request { type: 'digital', name: 'Broken', unit_amount: 25.00, currency: 'eur', purchasable: true }
    When method PUT
    * match [200, 201] contains responseStatus

    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { productId: 'SKU-BROKEN', quantity: 1 } ] }
    When method POST
    Then status 400

Scenario: a catalog item with a negative unit_amount is refused
    Given path '/restheart-test/catalog/SKU-NEGATIVE'
    And param wm = 'upsert'
    And header Authorization = adminAuth
    And request { type: 'digital', name: 'Negative', unit_amount: -500, currency: 'eur', purchasable: true }
    When method PUT
    * match [200, 201] contains responseStatus

    Given path '/restheart-test/orders'
    And header Authorization = authHeader
    And request { items: [ { productId: 'SKU-NEGATIVE', quantity: 1 } ] }
    When method POST
    Then status 400
