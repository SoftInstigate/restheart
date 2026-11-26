Feature: @teams, all the caller's teams, by id and by role, in ACL rules (#832)

  # @team is the active team; @teams is every team of the caller, as { _id, role }, with
  # @teams._id and @teams.<role> the arrays of ids. A credential that acts in every team of its
  # user names the team in the document, and the rules check it against @teams.
  #
  # The rules on /restheart-test/teams-scoped live in conf-overrides.yml: a POST passes only when
  # the team in the body is one the caller owns, in(value=@request.body.team, array=@teams.owner);
  # a GET reads {"team": {"$in": "@teams._id"}}.
  #
  # owner-test owns the first team and, after setup-second-team, is a member of the second one,
  # which the acceptance made active.

  Background:
    * url baseUrl
    * configure followRedirects = false
    # owner-test must exist before the second team invites it, or the invitation is one for a new
    # account, which only activation accepts: the order team-claim-variable.feature uses too
    * karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def secondSetup = karate.call('classpath:karate/accounts/helpers/setup-second-team.feature')
    * def secondTeamId = secondSetup.secondTeamId
    * def secondOwnerJwt = secondSetup.secondOwnerJwt
    * def ownerJwt = secondSetup.ownerAcceptJwt

    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    * def firstTeamId = karate.filter(response, function(x){ return x.role == 'owner' })[0].id
    * def otherTeamId = { "$oid": "000000000000000000000832" }

    # a clean collection, every run. A bulk delete refuses an empty filter with a 400
    Given path '/restheart-test/teams-scoped'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201] contains responseStatus

    Given path '/restheart-test/teams-scoped/*'
    And header Authorization = adminAuth
    And param filter = '{"_id": {"$exists": true}}'
    When method DELETE
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: an insert passes for a team the caller owns, whichever team is active
  # ---------------------------------------------------------------------------
    # the second team is the active one, and owner-test is only a member there
    Given path '/restheart-test/teams-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "title": "in the owned team", "team": "#(firstTeamId)" }
    When method POST
    Then status 201

  # ---------------------------------------------------------------------------
  Scenario: an insert is refused for a team where the caller is a member, for a team it is not in, and without a team
  # ---------------------------------------------------------------------------
    Given path '/restheart-test/teams-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "title": "in the team it is a member of", "team": "#(secondTeamId)" }
    When method POST
    Then status 403

    Given path '/restheart-test/teams-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "title": "in someone else's team", "team": "#(otherTeamId)" }
    When method POST
    Then status 403

    Given path '/restheart-test/teams-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "title": "nowhere" }
    When method POST
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: a bulk insert is refused, even when every document names a team the caller owns
  # ---------------------------------------------------------------------------
    # an array body leaves @request.body unbound, so the rule cannot hold
    Given path '/restheart-test/teams-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And request [{ "title": "one", "team": "#(firstTeamId)" }, { "title": "two", "team": "#(firstTeamId)" }]
    When method POST
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: a read sees the documents of every team of the caller, and only those; leaving a team takes its documents away at the next request
  # ---------------------------------------------------------------------------
    # one document per team, written by the admin, whom no rule restricts
    Given path '/restheart-test/teams-scoped'
    And header Authorization = adminAuth
    And request [{ "title": "first", "team": "#(firstTeamId)" }, { "title": "second", "team": "#(secondTeamId)" }, { "title": "other", "team": "#(otherTeamId)" }]
    When method POST
    Then status 200

    Given path '/restheart-test/teams-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And param rep = 's'
    And param sort = '{"title": 1}'
    When method GET
    Then status 200
    And match response[*].title == ['first', 'second']

    # the owner of the second team removes owner-test from it
    Given path '/auth/remove-member'
    And header Authorization = 'Bearer ' + secondOwnerJwt
    And request { "email": "owner-test@example.com" }
    When method DELETE
    Then status 200

    # the same token, the next request: the second team's document is gone
    Given path '/restheart-test/teams-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And param rep = 's'
    When method GET
    Then status 200
    And match response[*].title == ['first']
