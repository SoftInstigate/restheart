Feature: memberships live on the team, the user keeps the active team id (#766)

  # Since 9.12.0 DefaultMembershipProvider keeps who belongs to a team, and as what, in
  # team.members only; the user document keeps team: { _id }, the active team, nothing else.
  # A `teams` entry or a `team.role` left on a user document from before is neither read nor
  # written: the team decides.

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def ownerSetup = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = ownerSetup.ownerJwt
    Given path '/restheart-test/team-partitioned'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201, 409] contains responseStatus

  # ---------------------------------------------------------------------------
  Scenario: a membership the user document claims and the team does not confirm gives no access
  # ---------------------------------------------------------------------------
    * def secondSetup = karate.call('classpath:karate/accounts/helpers/setup-second-team.feature')
    * def secondTeamId = secondSetup.secondTeamId
    * def ownerJwt = secondSetup.ownerAcceptJwt

    # the setup made owner-test a member of the second team: take it out of the team, by hand
    Given path '/teams/' + secondTeamId['$oid']
    And header Authorization = adminAuth
    And request { "$pull": { "members": { "userId": "owner-test@example.com" } } }
    When method PATCH
    Then status 200

    # and leave the pre-9.12.0 claim of that membership on the user document
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And request { "teams": [ { "id": "#(secondTeamId)", "role": "member" } ] }
    When method PATCH
    Then status 200

    # the team decides: not listed, not switchable
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    * def teamIds = karate.map(response, function(x){ return x.id })
    And match teamIds !contains secondTeamId

    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamId": "#(secondTeamId)" }
    When method POST
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: an active team whose members do not include the user is no active membership
  # ---------------------------------------------------------------------------
    * def secondSetup = karate.call('classpath:karate/accounts/helpers/setup-second-team.feature')
    * def secondTeamId = secondSetup.secondTeamId
    * def ownerJwt = secondSetup.ownerAcceptJwt

    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200

    # the second team is the active one after the acceptance; take the user out of it
    Given path '/teams/' + secondTeamId['$oid']
    And header Authorization = adminAuth
    And request { "$pull": { "members": { "userId": "owner-test@example.com" } } }
    When method PATCH
    Then status 200

    # no active membership: @team.role resolves to nothing, so the owner-only rule cannot grant
    Given path '/restheart-test/team-partitioned'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "title": "nowhere to put it" }
    When method POST
    Then status 403

    Given path '/token'
    And header Authorization = 'Bearer ' + ownerJwt
    And param _claims-override = '@team'
    When method POST
    Then status 200
    * def payload = JSON.parse(new java.lang.String(java.util.Base64.getUrlDecoder().decode(response.access_token.split('.')[1])))
    And match payload.team == '#notpresent'

    # the team the user owns is still there to switch to, and the switch leaves { _id } only
    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    And match response == '#[1]'
    * def ownedTeamId = response[0].id

    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamId": "#(ownedTeamId)" }
    When method POST
    Then status 200
    And match response.team.role == 'owner'

    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.team == { _id: '#(ownedTeamId)' }
    And match response.teams == '#notpresent'
