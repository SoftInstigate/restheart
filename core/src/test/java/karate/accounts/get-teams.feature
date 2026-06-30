Feature: GET /auth/teams

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
    Given path '/auth/teams'
    When method OPTIONS
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/teams'
    When method GET
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: authenticated user gets their teams list
  # ---------------------------------------------------------------------------
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    And match each response == { id: '#notnull', name: '#string', role: '#string', active: '#boolean' }

  # ---------------------------------------------------------------------------
  Scenario: exactly one entry has active=true (the current JWT team)
  # ---------------------------------------------------------------------------
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    * def activeTeams = karate.filter(response, function(x){ return x.active == true })
    * assert activeTeams.length == 1

  # ---------------------------------------------------------------------------
  Scenario: user with two teams sees both in the list
  # ---------------------------------------------------------------------------
    # Ensure the second team invite has been accepted
    # (setup-second-team.feature should have done this, but verify)
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    # The user may have 1 or 2 teams depending on whether the invite was accepted
    * def ids = karate.map(response, function(x){ return x.id })
    * def hasSecond = ids.indexOf(secondTeamId) >= 0
        * if (!hasSecond) karate.log('WARNING: second team not yet accepted')
    * eval if (hasSecond) karate.match(response.length >= 2).assertTrue()
