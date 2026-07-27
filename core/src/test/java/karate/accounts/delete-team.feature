Feature: DELETE /auth/team

  # Tests for the team deletion endpoint.
  # X-Skip-Email: true is set globally in karate-config.js.

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def setupResult = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = setupResult.ownerJwt

  # ---------------------------------------------------------------------------
  Scenario: OPTIONS returns 200
  # ---------------------------------------------------------------------------
    Given path '/auth/team'
    When method OPTIONS
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/team'
    When method DELETE
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: a regular member cannot delete the team — returns 403
  # ---------------------------------------------------------------------------
    * def memberEmail = 'dt-403-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "DeleteTeam1!" }
    When method PATCH
    Then status 200
    * def memberJwt = responseHeaders['Set-Cookie'][0].split('Bearer_')[1].split(';')[0]

    Given path '/auth/team'
    And header Authorization = 'Bearer ' + memberJwt
    When method DELETE
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: owner cannot delete a team that still has other members — returns 409
  # ---------------------------------------------------------------------------
    * def memberEmail = 'dt-409-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "DeleteTeam1!" }
    When method PATCH
    Then status 200

    Given path '/auth/team'
    And header Authorization = 'Bearer ' + ownerJwt
    When method DELETE
    Then status 409

  # ---------------------------------------------------------------------------
  Scenario: happy path — owner deletes their sole-member team
  # ---------------------------------------------------------------------------
    # Read the team id before deletion
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def teamId = response.team._id['$oid']

    Given path '/auth/team'
    And header Authorization = 'Bearer ' + ownerJwt
    When method DELETE
    Then status 200

    # Verify the team document is gone
    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method GET
    Then status 404

    # Verify the caller's own membership pointer was cleared
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.teams == '#[0]'
    And match response.team == '#notpresent'

  # ---------------------------------------------------------------------------
  Scenario: calling delete again after the team is already gone returns 403 (no active team left)
  # ---------------------------------------------------------------------------
    Given path '/auth/team'
    And header Authorization = 'Bearer ' + ownerJwt
    When method DELETE
    Then status 200

    Given path '/auth/team'
    And header Authorization = 'Bearer ' + ownerJwt
    When method DELETE
    Then status 403
