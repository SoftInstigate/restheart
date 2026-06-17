Feature: POST /auth/invite

  # Tests for the team-invitation endpoint.
  # Uses ownerAuth (owner-test@example.com) which is created/activated once per run.
  # X-Skip-Email: true is set globally in karate-config.js.

  Background:
    * url baseUrl
    * configure followRedirects = false
    # Ensure owner-test@example.com exists and is active (idempotent)
    * def setupResult = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = setupResult.ownerJwt

  # ---------------------------------------------------------------------------
  Scenario: happy path — owner invites a new user returns 201
  # ---------------------------------------------------------------------------
    * def inviteEmail = 'invite-user-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(inviteEmail)", "role": "member" }
    When method POST
    Then status 201

  # ---------------------------------------------------------------------------
  Scenario: owner invites with role=owner returns 201
  # ---------------------------------------------------------------------------
    * def inviteEmail = 'invite-owner-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(inviteEmail)", "role": "owner" }
    When method POST
    Then status 201

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request — returns 401
  # ---------------------------------------------------------------------------
    * def inviteEmail = 'invite-unauth-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And request { "email": "#(inviteEmail)", "role": "member" }
    When method POST
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: authenticated user without owner/admin role — returns 403
  # ---------------------------------------------------------------------------
    # 1. Invite a plain user (role: user) using the owner
    * def regularEmail = 'regular-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(regularEmail)", "role": "member" }
    When method POST
    Then status 201

    # 2. Get invite token from auth_invitations
    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: regularEmail })
    * def inviteToken = tokenResult.result

    # 3. Activate the invited user (sets password, makes them active with role 'user')
    Given path '/auth/activate'
    And request
      """
      {
        "email":    "#(regularEmail)",
        "token":    "#(inviteToken)",
        "password": "Password123!",
        "consents": { "terms": true, "privacy": true }
      }
      """
    When method PATCH
    Then status 200
    # Capture JWT issued on activation
    * def activateCookieList = responseHeaders['Set-Cookie']
    * def activateCookie = activateCookieList != null && activateCookieList.length > 0 ? activateCookieList[0] : ''
    * def regularJwt = activateCookie.split('Bearer_')[1].split(';')[0]

    # 4. Attempt to invite with the regular (member-role) account — must be 403
    * def inviteTarget = 'invite-403-target-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + regularJwt
    And request { "email": "#(inviteTarget)", "role": "member" }
    When method POST
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: duplicate invite — inviting the same email twice returns 409
  # ---------------------------------------------------------------------------
    * def inviteEmail = 'invite-dup-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(inviteEmail)", "role": "member" }
    When method POST
    Then status 201

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(inviteEmail)", "role": "member" }
    When method POST
    Then status 409

  # ---------------------------------------------------------------------------
  Scenario: missing email in body — returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "role": "member" }
    When method POST
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: verify DB — after invite new user has roles=$unauthenticated and auth_invitations entry
  # ---------------------------------------------------------------------------
    * def inviteEmail = 'invite-db-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(inviteEmail)", "role": "member" }
    When method POST
    Then status 201

    # User document must have $unauthenticated role but NO inviteToken field
    Given path '/users/' + inviteEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.roles contains '$unauthenticated'
    And match response.inviteToken == '#notpresent'
    And match response.inviteCreatedAt == '#notpresent'

    # Invitation token lives in auth_invitations
    Given path '/auth_invitations'
    And header Authorization = adminAuth
    And param filter = '{"email":"' + inviteEmail + '"}'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length == 1
    And match response[0].isNewUser == true
    And match response[0].token == '#notnull'
    And match response[0].expiresAt == '#notnull'
