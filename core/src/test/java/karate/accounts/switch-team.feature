Feature: POST /auth/switch-team

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def ownerSetup = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = ownerSetup.ownerJwt
    * def secondSetup = karate.call('classpath:karate/accounts/helpers/setup-second-team.feature')
    * def secondTeamId = secondSetup.secondTeamId
    # Accept the invitation (setup-second-team already invited owner-test)
    * def inviteResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: 'owner-test@example.com' })
    * if (inviteResult.inviteToken) karate.call('classpath:karate/accounts/helpers/accept-invite-clean.feature', { jwt: ownerJwt, token: inviteResult.inviteToken })


  # ---------------------------------------------------------------------------
  Scenario: OPTIONS returns 200
  # ---------------------------------------------------------------------------
    Given path '/auth/switch-team'
    When method OPTIONS
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/switch-team'
    And request { "teamId": "someid" }
    When method POST
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: missing teamId returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request {}
    When method POST
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: teamId not in user.s membership returns 403
  # ---------------------------------------------------------------------------
    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamId": "000000000000000000000000" }
    When method POST
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: switch to second team returns 200 with new cookie
  # ---------------------------------------------------------------------------
    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamId": "#(secondTeamId)" }
    When method POST
    Then status 200
    And match response.team._id == secondTeamId
    And match response.team.role == 'member'
    And match response.role == 'member'
    And match responseHeaders['Set-Cookie'] != null

  # ---------------------------------------------------------------------------
  Scenario: after switch, active team changes in GET /auth/teams
  # ---------------------------------------------------------------------------
    # 1. Switch to second team
    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamId": "#(secondTeamId)" }
    When method POST
    Then status 200
    * def switchCookie = responseHeaders['Set-Cookie'].length > 0 ? responseHeaders['Set-Cookie'][0] : ''
    * def newJwt = switchCookie.split('Bearer_')[1].split(';')[0]

    # 2. Verify new JWT has the new active team
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + newJwt
    When method GET
    Then status 200
    * def activeTeams = karate.filter(response, function(x){ return x.active == true })
    * assert activeTeams.length == 1
    And match activeTeams[0].id == secondTeamId

  # ---------------------------------------------------------------------------
  Scenario: switch back to original team works
  # ---------------------------------------------------------------------------
    # Get original team
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    * def originalTeam = karate.filter(response, function(x){ return x.active == true })[0]
    * def originalTeamId = originalTeam.id

    # Switch to second team
    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamId": "#(secondTeamId)" }
    When method POST
    Then status 200
    * def switchCookie = responseHeaders['Set-Cookie'].length > 0 ? responseHeaders['Set-Cookie'][0] : ''
    * def newJwt = switchCookie.split('Bearer_')[1].split(';')[0]

    # Switch back
    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + newJwt
    And request { "teamId": "#(originalTeamId)" }
    When method POST
    Then status 200
    And match response.team._id == originalTeamId

  # ---------------------------------------------------------------------------
  Scenario: verify DB — invite with existing user adds teams entry
  # ---------------------------------------------------------------------------
    # Check owner-test has orgs array with both teams
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.teams == '#array'
    * assert response.teams.length >= 2
    * def teamIds = karate.map(response.teams, function(x){ return x.id })
    And match teamIds contains secondTeamId
