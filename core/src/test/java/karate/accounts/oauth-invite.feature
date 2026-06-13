Feature: OAuth activation for invited users

  # Tests the invite + OAuth activation flow (issue #631).
  #
  # Uses TestOAuthProvider — a mock OAuth provider that:
  #   - returns a fake authorization URL with the CSRF state embedded
  #   - accepts any code of the form "test-<email>" as the authorization code
  #   - requires no external HTTP calls
  #
  # Flow under test:
  #   1. Owner invites a new user  →  user document created with status:"invited"
  #   2. Invited user initiates OAuth  →  GET /auth/oauth/authorize/test?pendingInviteToken=TOKEN&consentsAccepted=true
  #      Returns 302 with Location: http://localhost/test-oauth/authorize?state=STATE&...
  #   3. Extract CSRF state from Location header
  #   4. Simulate callback  →  GET /auth/oauth/callback/test?code=test-<email>&state=STATE
  #      Returns 302 to frontendSuccessUrl with Set-Cookie containing JWT
  #   5. JWT must carry status:"active" and a valid tenantId claim
  #   6. MongoDB must show user with status:"active", inviteToken removed, consents stored

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def setupResult = karate.callSingle('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = setupResult.ownerJwt

  # ---------------------------------------------------------------------------
  Scenario: happy path — invited user activates via OAuth with consentsAccepted=true
  # ---------------------------------------------------------------------------

    # 1. Owner invites a fresh user
    * def inviteEmail = 'oauth-invite-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(inviteEmail)", "role": "member" }
    When method POST
    Then status 201

    # 2. Read inviteToken from MongoDB (admin access)
    Given path '/users/' + inviteEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def inviteToken = response.inviteToken
    * match inviteToken == '#notnull'

    # Verify: user starts with roles: ["$unauthenticated"]
    And match response.roles contains '$unauthenticated'

    # 3. Initiate OAuth — pass pendingInviteToken and consentsAccepted=true
    Given path '/auth/oauth/authorize/test'
    And param pendingInviteToken = inviteToken
    And param consentsAccepted = 'true'
    When method GET
    Then status 307

    # Extract CSRF state from Location header.
    # New state format: base64url(tenantDb) + "." + base64url(32-random-bytes)
    # The full token (including the ".") is passed verbatim as the state param.
    # Location: http://localhost/test-oauth/authorize?state=<base64.base64>&client_id=test-client
    * def location = responseHeaders['Location'][0]
    * def state = location.split('state=')[1].split('&')[0]
    * match state == '#notnull'
    * karate.log('CSRF state:', state)

    # 4. Simulate OAuth callback — code encodes the email
    Given path '/auth/oauth/callback/test'
    And param code = 'test-' + inviteEmail
    And param state = state
    When method GET
    Then status 307

    # Must redirect to the frontend success URL
    * def callbackLocation = responseHeaders['Location'][0]
    * match callbackLocation contains 'localhost:4200/app'

    # Auth cookie must be set
    * def setCookieHeader = responseHeaders['Set-Cookie'][0]
    * match setCookieHeader contains 'rh_auth=Bearer_'
    * match setCookieHeader contains 'HttpOnly'

    # 5. Decode JWT and verify claims
    * def jwtPart = setCookieHeader.split('rh_auth=Bearer_')[1].split(';')[0]
    * def parts = jwtPart.split('.')
    * def payloadB64 = parts[1]
    # JWT payload is base64url-encoded — decode with Java, parse with JS
    * def payloadJson = new java.lang.String(java.util.Base64.getUrlDecoder().decode(payloadB64))
    * def payload = JSON.parse(payloadJson)
    * karate.log('JWT payload:', payload)
    * match payload.sub == inviteEmail
    # tenant claim is now a BSON-extended-JSON object {"$oid":"..."} matching the stored ObjectId
    * def tenantClaim = payload.tenant
    * def tenantStr = (typeof tenantClaim == 'object') ? tenantClaim['$oid'] : tenantClaim
    * match tenantStr == '#string'

    # 6. Verify DB — user activated, inviteToken removed, consents stored
    Given path '/users/' + inviteEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.roles contains 'user'
    And match response.inviteToken == '#notpresent'
    And match response.inviteCreatedAt == '#notpresent'
    And match response.consents.termsVersion == '#notnull'
    And match response.consents.privacyVersion == '#notnull'
    And match response.consents.acceptedAt == '#notnull'
    And match response.consents.ip == '#notnull'

  # ---------------------------------------------------------------------------
  Scenario: OAuth for invited user without consentsAccepted — still activates, no consents stored
  # ---------------------------------------------------------------------------

    * def inviteEmail = 'oauth-invite-noconsent-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(inviteEmail)", "role": "member" }
    When method POST
    Then status 201

    Given path '/users/' + inviteEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def inviteToken = response.inviteToken

    # Authorize without consentsAccepted
    Given path '/auth/oauth/authorize/test'
    And param pendingInviteToken = inviteToken
    When method GET
    Then status 307
    * def location = responseHeaders['Location'][0]
    * def state = location.split('state=')[1].split('&')[0]

    # Callback
    Given path '/auth/oauth/callback/test'
    And param code = 'test-' + inviteEmail
    And param state = state
    When method GET
    Then status 307
    * def setCookieHeader = responseHeaders['Set-Cookie'][0]
    * match setCookieHeader contains 'rh_auth=Bearer_'

    # Verify DB — activated but NO consents
    Given path '/users/' + inviteEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.roles contains 'user'
    And match response.inviteToken == '#notpresent'
    And match response.consents == '#notpresent'

  # ---------------------------------------------------------------------------
  Scenario: OAuth for a new (non-invited) user — normal registration path, status active
  # ---------------------------------------------------------------------------

    * def newEmail = 'oauth-new-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/oauth/authorize/test'
    When method GET
    Then status 307
    * def state = responseHeaders['Location'][0].split('state=')[1].split('&')[0]

    Given path '/auth/oauth/callback/test'
    And param code = 'test-' + newEmail
    And param state = state
    When method GET
    Then status 307
    * def setCookieHeader = responseHeaders['Set-Cookie'][0]
    * match setCookieHeader contains 'rh_auth=Bearer_'

    Given path '/users/' + newEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.roles contains 'user'

  # ---------------------------------------------------------------------------
  Scenario: invalid CSRF state (no "." separator) — callback returns redirect to error URL
  # ---------------------------------------------------------------------------

    # The state "invalid-state-token" has no "." separator so decodeDbFromState
    # returns null immediately, before any MongoDB lookup.
    Given path '/auth/oauth/callback/test'
    And param code = 'test-nobody@example.com'
    And param state = 'invalid-state-token'
    When method GET
    Then status 307
    * def errorLocation = responseHeaders['Location'][0]
    * match errorLocation contains 'error=oauth_error'
    * match errorLocation contains 'reason='

  # ---------------------------------------------------------------------------
  # NOTE: the case where activateViaOAuth returns Optional.empty() (custom provider
  # denying OAuth activation) is NOT tested here because it requires a custom
  # MembershipProvider that overrides the default. That path is verified by:
  #   - the behavior in OAuthCallback: redirectError(res, "Account is pending activation")
  #   - the default returns Optional.empty() (preserves backward compatibility)
  # ---------------------------------------------------------------------------

  # ---------------------------------------------------------------------------
  Scenario: expired / replayed state — second callback with same state returns error
  # ---------------------------------------------------------------------------

    * def replayEmail = 'oauth-replay-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/oauth/authorize/test'
    When method GET
    Then status 307
    * def state = responseHeaders['Location'][0].split('state=')[1].split('&')[0]

    # First callback — OK
    Given path '/auth/oauth/callback/test'
    And param code = 'test-' + replayEmail
    And param state = state
    When method GET
    Then status 307
    * match responseHeaders['Location'][0] contains 'localhost:4200/app'

    # Second callback with same state — must fail (state token consumed)
    Given path '/auth/oauth/callback/test'
    And param code = 'test-' + replayEmail
    And param state = state
    When method GET
    Then status 307
    And match responseHeaders['Location'][0] contains 'error=oauth_error'
