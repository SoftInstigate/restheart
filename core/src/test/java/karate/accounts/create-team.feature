Feature: POST /auth/teams

  # Tests for the additional-team-creation endpoint.
  # X-Skip-Email: true is set globally in karate-config.js.

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def setupResult = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = setupResult.ownerJwt

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
    And request { "teamName": "New Workspace" }
    When method POST
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: missing teamName returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    And request {}
    When method POST
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: blank teamName returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamName": "   " }
    When method POST
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: happy path — owner creates a second team, becomes its owner and active member
  # ---------------------------------------------------------------------------
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamName": "New Workspace" }
    When method POST
    Then status 201
    And match response.id == '#notnull'
    And match response.name == 'New Workspace'
    And match response.role == 'owner'
    And match responseHeaders['Set-Cookie'] != null

  # ---------------------------------------------------------------------------
  Scenario: verify DB — new team becomes the caller's active team and is added to teams[]
  # ---------------------------------------------------------------------------
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamName": "Second Workspace" }
    When method POST
    Then status 201
    * def newTeamId = response.id['$oid']

    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.teams == '#[2]'
    * def newEntry = karate.filter(response.teams, function(x){ return x.id['$oid'] == newTeamId })[0]
    And match newEntry.role == 'owner'
    And match response.team._id['$oid'] == newTeamId
    And match response.team.role == 'owner'

  # ---------------------------------------------------------------------------
  Scenario: verify DB — the team document itself is created correctly
  # ---------------------------------------------------------------------------
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamName": "Third Workspace" }
    When method POST
    Then status 201
    * def newTeamId = response.id['$oid']

    Given path '/teams/' + newTeamId
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.name == 'Third Workspace'
    And match response.createdBy == 'owner-test@example.com'
    And match response.members == '#[1]'
    And match response.members[0].userId == 'owner-test@example.com'
    And match response.members[0].role == 'owner'
