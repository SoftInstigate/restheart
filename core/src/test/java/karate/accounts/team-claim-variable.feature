Feature: "@team" in account-properties-claims, and @team in ACL rules (#765)

  # The accounts endpoints put the active team in the JWT as { _id, role }, built from the
  # membership provider. POST /token used to copy whatever the user document stores instead.
  # With the entry "@team", resolved through the ACL variable restheart-accounts registers,
  # both issue the same claim; and an ACL rule on @team._id reads the team from the provider,
  # whichever token the caller holds.
  #
  # The entry reaches POST /token as a per-request override (?_claims-override, attached by
  # accountPropertiesClaimsOverrideInterceptor), so the static test configuration is untouched.
  # Basic maps to the file realm in this configuration, so the owner reaches /token with its JWT
  # as Bearer: the same JwtTokenManager path, and a stronger proof, since that token already
  # says a team the new one must not copy.
  # The ACL rule on /restheart-test/team-scoped lives in conf-overrides.yml.

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def ownerSetup = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = ownerSetup.ownerJwt
    * def Base64 = Java.type('java.util.Base64')
    * def payloadOf = function(jwt){ return JSON.parse(new java.lang.String(Base64.getUrlDecoder().decode(jwt.split('.')[1]))) }
    Given path '/restheart-test/team-scoped'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201, 409] contains responseStatus

  # ---------------------------------------------------------------------------
  Scenario: POST /token with "@team" carries the claim the accounts endpoints build
  # ---------------------------------------------------------------------------
    * def verifiedTeam = payloadOf(ownerJwt).team
    And match verifiedTeam == { _id: { '$oid': '#string' }, role: 'owner' }

    Given path '/token'
    And header Authorization = 'Bearer ' + ownerJwt
    And param _claims-override = '@team'
    When method POST
    Then status 200
    * def loginTeam = payloadOf(response.access_token).team
    And match loginTeam == verifiedTeam

  # ---------------------------------------------------------------------------
  Scenario: after a switch, POST /token says the new team, as POST /auth/switch-team does
  # ---------------------------------------------------------------------------
    * def secondSetup = karate.call('classpath:karate/accounts/helpers/setup-second-team.feature')
    * def secondTeamId = secondSetup.secondTeamId
    # the setup registers the owner afresh and accepts the invitation for it: this is its token,
    # and the acceptance made the second team the active one. Back to the first.
    * def ownerJwt = secondSetup.ownerAcceptJwt
    * def firstTeam = payloadOf(ownerJwt).team
    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamId": "#(firstTeam._id)" }
    When method POST
    Then status 200

    # a document in the first team, stamped by mergeRequest from @team._id
    Given path '/restheart-test/team-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "title": "in the first team" }
    When method POST
    Then status 201

    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamId": "#(secondTeamId)" }
    When method POST
    Then status 200
    * def switchedTeam = response.team
    And match switchedTeam.role == 'member'

    Given path '/token'
    And header Authorization = 'Bearer ' + ownerJwt
    And param _claims-override = '@team'
    When method POST
    Then status 200
    * def loginTeam = payloadOf(response.access_token).team
    And match loginTeam == switchedTeam

    # @team comes from the membership provider, not from the token: the token issued before the
    # switch now reads as the second team, which has no documents
    Given path '/restheart-test/team-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And param rep = 's'
    When method GET
    Then status 200
    And match response == []

  # ---------------------------------------------------------------------------
  Scenario: a readFilter on @team._id returns the same documents to a /token token and to an accounts token
  # ---------------------------------------------------------------------------
    Given path '/restheart-test/team-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "title": "scoped by the active team" }
    When method POST
    Then status 201

    # the token /token issues: no "@team" entry needed, the rule does not read the token
    Given path '/token'
    And header Authorization = 'Bearer ' + ownerJwt
    When method POST
    Then status 200
    * def loginJwt = response.access_token

    Given path '/restheart-test/team-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'
    * def seenWithAccountsToken = response

    Given path '/restheart-test/team-scoped'
    And header Authorization = 'Bearer ' + loginJwt
    And param rep = 's'
    When method GET
    Then status 200
    And match response == seenWithAccountsToken
