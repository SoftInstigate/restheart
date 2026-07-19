Feature: PATCH /auth/team

  # Tests for the team rename/edit endpoint.
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
    And request { "name": "New Name" }
    When method PATCH
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: empty body returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request {}
    When method PATCH
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: blank name returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "name": "  " }
    When method PATCH
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: a regular member cannot update the team — returns 403
  # ---------------------------------------------------------------------------
    * def memberEmail = 'ut-403-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "UpdateTeam1!" }
    When method PATCH
    Then status 200
    * def memberJwt = responseHeaders['Set-Cookie'][0].split('Bearer_')[1].split(';')[0]

    Given path '/auth/team'
    And header Authorization = 'Bearer ' + memberJwt
    And request { "name": "Hijacked Name" }
    When method PATCH
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: happy path — owner updates name and description
  # ---------------------------------------------------------------------------
    Given path '/auth/team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "name": "Renamed Team", "description": "A brand new description" }
    When method PATCH
    Then status 200

    # Verify in DB
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def teamId = response.team['$oid']

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.name == 'Renamed Team'
    And match response.description == 'A brand new description'

  # ---------------------------------------------------------------------------
  Scenario: partial update — only name is changed, description untouched
  # ---------------------------------------------------------------------------
    Given path '/auth/team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "description": "Initial description" }
    When method PATCH
    Then status 200

    Given path '/auth/team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "name": "Only Name Changed" }
    When method PATCH
    Then status 200

    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def teamId = response.team['$oid']

    Given path '/teams/' + teamId
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.name == 'Only Name Changed'
    And match response.description == 'Initial description'
