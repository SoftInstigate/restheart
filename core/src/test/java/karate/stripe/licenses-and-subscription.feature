Feature: /stripe/licenses seat licensing and GET /stripe/subscription

# The owner's team starts on the default 'free' plan (capped, max 1 seat) — no seeding
# needed, this is what a freshly registered team already has. Grants /stripe/checkout /
# /stripe/portal / /stripe/subscription / /stripe/licenses to role 'user' — see the
# fileAclAuthorizer permissions in conf-overrides.yml.

Background:
    * url baseUrl
    * configure followRedirects = false
    * def ownerSetup = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = ownerSetup.ownerJwt
    * def authHeader = 'Bearer ' + ownerJwt

Scenario: a free-plan owner reads their own default subscription state
    Given path '/stripe/subscription'
    And header Authorization = authHeader
    When method GET
    Then status 200
    And match response.plan == 'free'
    And match response.active == false
    And match response.licensed == false
    And match response.seats.limit == 1
    And match response.seats.licensed == 0
    And match response.seats.available == 1
    And match response.seats.over_limit == false
    # no Stripe subscription ever existed for this owner — 'status' must be absent, not null
    And match response.status == '#notpresent'

Scenario: owner grants themselves a seat licence, at the free plan's 1-seat cap
    Given path '/stripe/licenses'
    And header Authorization = authHeader
    And request { userId: '#(ownerEmail)' }
    When method POST
    Then status 201

    Given path '/stripe/subscription'
    And header Authorization = authHeader
    When method GET
    Then status 200
    And match response.licensed == true
    And match response.seats.licensed == 1
    And match response.seats.available == 0

Scenario: granting a licence already held is idempotent, not an error
    Given path '/stripe/licenses'
    And header Authorization = authHeader
    And request { userId: '#(ownerEmail)' }
    When method POST
    Then status 201

    Given path '/stripe/licenses'
    And header Authorization = authHeader
    And request { userId: '#(ownerEmail)' }
    When method POST
    Then status 200

Scenario: granting a licence to a member that does not exist in the team returns 404
    Given path '/stripe/licenses'
    And header Authorization = authHeader
    And request { userId: 'nobody-#(ownerEmail)' }
    When method POST
    Then status 404

Scenario: granting past the seat limit returns 409, for a member that genuinely exists
    # A real second member is required here — a userId absent from the team would hit
    # MEMBER_NOT_FOUND (404) rather than exercise the seat-limit check (409) at all.
    * def member2Email = 'member2-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = authHeader
    And request { email: '#(member2Email)', role: 'member' }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: member2Email })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { email: '#(member2Email)', token: '#(inviteToken)', password: 'Password123!', consents: { terms: true, privacy: true } }
    When method PATCH
    Then status 200

    # The free plan's 1-seat cap is reached by licensing the owner first.
    Given path '/stripe/licenses'
    And header Authorization = authHeader
    And request { userId: '#(ownerEmail)' }
    When method POST
    Then status 201

    Given path '/stripe/licenses'
    And header Authorization = authHeader
    And request { userId: '#(member2Email)' }
    When method POST
    Then status 409

Scenario: revoking a licence frees the seat for a subsequent grant
    Given path '/stripe/licenses'
    And header Authorization = authHeader
    And request { userId: '#(ownerEmail)' }
    When method POST
    Then status 201

    Given path '/stripe/licenses'
    And header Authorization = authHeader
    And request { userId: '#(ownerEmail)' }
    When method DELETE
    Then status 200

    Given path '/stripe/subscription'
    And header Authorization = authHeader
    When method GET
    Then status 200
    And match response.licensed == false
    And match response.seats.available == 1

    Given path '/stripe/licenses'
    And header Authorization = authHeader
    And request { userId: '#(ownerEmail)' }
    When method POST
    Then status 201

Scenario: GET /stripe/licenses lists the licensed member and seat counts
    Given path '/stripe/licenses'
    And header Authorization = authHeader
    And request { userId: '#(ownerEmail)' }
    When method POST
    Then status 201

    Given path '/stripe/licenses'
    And header Authorization = authHeader
    When method GET
    Then status 200
    * def expectedLicensed = [ '#(ownerEmail)' ]
    And match response.licensed == expectedLicensed
    And match response.seats == { limit: 1, licensed: 1, available: 0 }
