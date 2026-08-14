Feature: stripe endpoints require authentication

# Every /stripe/* endpoint except /stripe/webhook requires an authenticated caller
# (see StripeCheckoutService, StripePortalService, StripeSubscriptionService,
# StripeLicensesService, each checking req.isAuthenticated() before doing anything
# else). None of these scenarios need a live Stripe connection: the request never
# gets far enough to call the Stripe API.

Background:
    * url 'http://localhost:8080'

Scenario: POST /stripe/checkout without credentials is rejected
    Given path '/stripe/checkout'
    And request { plan: 'gold', interval: 'month' }
    When method POST
    Then status 401

Scenario: POST /stripe/portal without credentials is rejected
    Given path '/stripe/portal'
    When method POST
    Then status 401

Scenario: GET /stripe/subscription without credentials is rejected
    Given path '/stripe/subscription'
    When method GET
    Then status 401

Scenario: POST /stripe/licenses without credentials is rejected
    Given path '/stripe/licenses'
    And request { userId: 'u1' }
    When method POST
    Then status 401

Scenario: DELETE /stripe/licenses without credentials is rejected
    Given path '/stripe/licenses'
    And request { userId: 'u1' }
    When method DELETE
    Then status 401
