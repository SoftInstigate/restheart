Feature: no team claim in the accounts tokens, @team in ACL rules, "@team" in account-properties-claims (#765)

  # Since 9.12.0 the tokens restheart-accounts issues carry no team claim: ACL rules read the
  # active team through the @team variable, from the membership provider, whichever token the
  # caller holds. A deployment that wants the team in the token lists "@team" in
  # account-properties-claims and gets it as { _id, role } on every path, /token included.
  #
  # The entry reaches the issuers as a per-request override (?_claims-override, attached by
  # accountPropertiesClaimsOverrideInterceptor), so the static test configuration is untouched.
  # Basic maps to the file realm in this configuration, so the owner reaches /token with its JWT
  # as Bearer: the same JwtTokenManager path.
  # The ACL rule on /restheart-test/team-scoped lives in conf-overrides.yml.

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def ownerSetup = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = ownerSetup.ownerJwt
    * def Base64 = Java.type('java.util.Base64')
    * def payloadOf = function(jwt){ return JSON.parse(new java.lang.String(Base64.getUrlDecoder().decode(jwt.split('.')[1]))) }
    * def cookieJwt = function(headers){ return headers['Set-Cookie'][0].split('Bearer_')[1].split(';')[0] }
    Given path '/restheart-test/team-scoped'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201, 409] contains responseStatus

  # ---------------------------------------------------------------------------
  Scenario: the verification token carries no team claim; "@team" puts it in the /token one
  # ---------------------------------------------------------------------------
    * def verified = payloadOf(ownerJwt)
    And match verified.team == '#notpresent'

    # the active team as the membership provider stores it, read by the admin
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def storedTeamId = response.team._id

    Given path '/token'
    And header Authorization = 'Bearer ' + ownerJwt
    When method POST
    Then status 200
    * def plain = payloadOf(response.access_token)
    And match plain.team == '#notpresent'

    Given path '/token'
    And header Authorization = 'Bearer ' + ownerJwt
    And param _claims-override = '@team'
    When method POST
    Then status 200
    * def withTeam = payloadOf(response.access_token)
    And match withTeam.team == { _id: '#(storedTeamId)', role: 'owner' }

  # ---------------------------------------------------------------------------
  Scenario: after a switch, every path says the new team, and the token issued before reads as it too
  # ---------------------------------------------------------------------------
    * def secondSetup = karate.call('classpath:karate/accounts/helpers/setup-second-team.feature')
    * def secondTeamId = secondSetup.secondTeamId
    # the setup registers the owner afresh and accepts the invitation for it: this is its token,
    # and the acceptance made the second team the active one. Back to the first, the one it owns.
    * def ownerJwt = secondSetup.ownerAcceptJwt

    Given path '/auth/teams'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    * def firstTeamId = karate.filter(response, function(x){ return x.role == 'owner' })[0].id

    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "teamId": "#(firstTeamId)" }
    When method POST
    Then status 200
    And match response.team == { _id: '#(firstTeamId)', role: 'owner' }
    # the body answers the action; the token says nothing about the team
    * def switchedPlain = payloadOf(cookieJwt(responseHeaders))
    And match switchedPlain.team == '#notpresent'

    # a document in the first team, stamped by mergeRequest from @team._id
    Given path '/restheart-test/team-scoped'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "title": "in the first team" }
    When method POST
    Then status 201

    # to the second team, asking for the claim: the accounts path puts it in like /token does
    Given path '/auth/switch-team'
    And header Authorization = 'Bearer ' + ownerJwt
    And param _claims-override = '@team'
    And request { "teamId": "#(secondTeamId)" }
    When method POST
    Then status 200
    * def switchedTeam = response.team
    And match switchedTeam.role == 'member'
    * def switchedWithTeam = payloadOf(cookieJwt(responseHeaders))
    And match switchedWithTeam.team == switchedTeam

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
