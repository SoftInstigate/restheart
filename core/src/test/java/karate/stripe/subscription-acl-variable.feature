Feature: @subscription ACL variable

# conf-overrides.yml grants role 'user' access to /restheart-test/stripe-gated only when
# equals[@subscription.plan, "gold"] — see the fileAclAuthorizer permissions list. This
# proves the chain end to end: a subscription state written to the team document is read
# by SubscriptionVarResolver and reaches the Undertow predicate engine.

Background:
    * url baseUrl
    * configure followRedirects = false

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

    * def ownerSetup = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = ownerSetup.ownerJwt
    * def authHeader = 'Bearer ' + ownerJwt

    Given path '/auth/teams'
    And header Authorization = authHeader
    When method GET
    Then status 200
    * def activeTeams = karate.filter(response, function(x){ return x.active == true })
    * def teamId = activeTeams[0].id

Scenario: a free-plan owner is denied a path gated on equals[@subscription.plan, "gold"]
    Given path '/restheart-test/stripe-gated/free-owner-doc'
    And header Authorization = authHeader
    And request {}
    When method PUT
    Then status 403

Scenario: a gold-plan owner is allowed through the same gate
    # Admin writes the subscription state directly — equivalent to what a webhook-driven
    # state update would leave behind.
    Given path '/restheart-test/teams/' + teamId
    And header Authorization = adminAuth
    And request { subscription: { plan: 'gold', status: 'active', seats: 1, cancel_at_period_end: false } }
    When method PATCH
    Then status 200

    Given path '/restheart-test/stripe-gated/gold-owner-doc'
    And header Authorization = authHeader
    And request {}
    When method PUT
    * match [200, 201] contains responseStatus

    # Cleanup.
    Given path '/restheart-test/stripe-gated/gold-owner-doc'
    And header Authorization = adminAuth
    When method DELETE
    Then status 204
