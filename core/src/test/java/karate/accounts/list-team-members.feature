Feature: GET /auth/team/members

  # Tests for the team member listing endpoint.
  # X-Skip-Email: true is set globally in karate-config.js.
  #
  # Background (per scenario): sets up owner-test@example.com.

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def setupResult = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = setupResult.ownerJwt

  # ---------------------------------------------------------------------------
  Scenario: OPTIONS returns 200
  # ---------------------------------------------------------------------------
    Given path '/auth/team/members'
    When method OPTIONS
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/team/members'
    When method GET
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: freshly registered owner sees only themselves
  # ---------------------------------------------------------------------------
    Given path '/auth/team/members'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    And match response == '#[1]'
    And match response[0] == { email: 'owner-test@example.com', name: 'Owner Test', role: 'owner', joinedAt: '#string' }

  # ---------------------------------------------------------------------------
  Scenario: after inviting and activating a member, both appear in the list
  # ---------------------------------------------------------------------------
    * def memberEmail = 'ltm-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "ListMembers1!" }
    When method PATCH
    Then status 200

    Given path '/auth/team/members'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    And match response == '#[2]'
    * def emails = karate.map(response, function(x){ return x.email })
    And match emails contains memberEmail
    And match emails contains 'owner-test@example.com'
    * def memberEntry = karate.filter(response, function(x){ return x.email == memberEmail })[0]
    And match memberEntry.role == 'member'

  # ---------------------------------------------------------------------------
  Scenario: a regular (non-owner) member can also list the team's members
  # ---------------------------------------------------------------------------
    * def memberEmail = 'ltm-asmember-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "ListMembers1!" }
    When method PATCH
    Then status 200
    * def memberJwt = responseHeaders['Set-Cookie'][0].split('Bearer_')[1].split(';')[0]

    Given path '/auth/team/members'
    And header Authorization = 'Bearer ' + memberJwt
    When method GET
    Then status 200
    And match response == '#[2]'
