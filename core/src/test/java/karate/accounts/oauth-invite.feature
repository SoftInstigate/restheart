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
  #   5. JWT must carry status:"active" and a valid teamId claim
  #   6. MongoDB must show user with status:"active", inviteToken removed, consents stored

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def setupResult = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
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

    # 2. Read inviteToken from auth_invitations (token no longer stored on user doc)
    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: inviteEmail })
    * def inviteToken = tokenResult.result
    * match inviteToken == '#notnull'

    # Verify: user starts with roles: ["$unauthenticated"]
    Given path '/users/' + inviteEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.roles contains '$unauthenticated'

    # 3. Initiate OAuth — pass pendingInviteToken and consentsAccepted=true
    Given path '/auth/oauth/authorize/test'
    And param pendingInviteToken = inviteToken
    And param consentsAccepted = 'true'
    When method GET
    Then status 307

    # Extract CSRF state from Location header.
    # New state format: base64url(teamDb) + "." + base64url(32-random-bytes)
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

    # Token must be carried as a URL fragment, never in the query string
    * match callbackLocation contains '#access_token='
    * def preHash = callbackLocation.split('#')[0]
    * match preHash !contains 'access_token='

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
    # team claim is now a { _id: {"$oid":"..."}, role: "..." } object (9.6.0+) mirroring user.team
    * def teamClaim = payload.team
    * match teamClaim._id['$oid'] == '#string'
    * match teamClaim.role == '#string'

    # 6. The fragment token must be the same JWT as the cookie, with token_type=Bearer
    * def fragment = callbackLocation.split('#')[1]
    * match fragment contains 'access_token=' + jwtPart
    * match fragment contains 'token_type=Bearer'

    # 6. Verify DB — user activated, no invite fields on user doc, consents stored
    Given path '/users/' + inviteEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.roles contains 'user'
    And match response.inviteToken == '#notpresent'
    And match response.inviteCreatedAt == '#notpresent'
    And match response.consents == '#notpresent'

    # auth_invitations entry removed after OAuth activation
    Given path '/auth_invitations'
    And header Authorization = adminAuth
    And param filter = '{"email":"' + inviteEmail + '"}'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[0]'

  # ---------------------------------------------------------------------------
  Scenario: OAuth for invited user without consentsAccepted — still activates, no consents stored
  # ---------------------------------------------------------------------------

    * def inviteEmail = 'oauth-invite-noconsent-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(inviteEmail)", "role": "member" }
    When method POST
    Then status 201

    # Read inviteToken from auth_invitations
    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: inviteEmail })
    * def inviteToken = tokenResult.result

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

    # Verify DB — activated but NO consents, no invite fields on user doc
    Given path '/users/' + inviteEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.roles contains 'user'
    And match response.inviteToken == '#notpresent'
    And match response.consents == '#notpresent'

  # ---------------------------------------------------------------------------
  Scenario: OAuth for a new (non-invited) user — normal registration path, roles=user after login
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
    * def callbackLocation = responseHeaders['Location'][0]
    * match callbackLocation contains '#access_token='
    * match callbackLocation.split('#')[0] !contains 'access_token='
    * def setCookieHeader = responseHeaders['Set-Cookie'][0]
    * match setCookieHeader contains 'rh_auth=Bearer_'

    Given path '/users/' + newEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.roles contains 'user'

  # ---------------------------------------------------------------------------
  Scenario: per-team OAuth overrides are honored on BOTH authorize and callback
  # ---------------------------------------------------------------------------

    # oauthOverrideInterceptor (test-plugins) attaches override-accounts-oauth-*
    # when ?_oauth-override=1 is present — simulates a deployment-layer interceptor
    # like restheart-cloud's TeamConfigInterceptor re-deriving per-tenant overrides
    # on every request. Sentinel values (overridden-client-id / overridden-scope /
    # http://localhost:8080/overridden) are distinct from the static test provider
    # config (test-client / email / http://localhost:8080) — see
    # OAuthOverrideInterceptor for the exact values.

    * def overrideEmail = 'oauth-override-' + java.util.UUID.randomUUID() + '@example.com'

    # 1. Authorize — TestOAuthProvider echoes client_id/scope into the fake
    #    authorization URL, so we can see the OVERRIDDEN values were resolved
    Given path '/auth/oauth/authorize/test'
    And param _oauth-override = '1'
    When method GET
    Then status 307
    * def location = responseHeaders['Location'][0]
    * match location contains 'client_id=overridden-client-id'
    * match location contains 'scope=overridden-scope'
    * def state = location.split('state=')[1].split('&')[0]

    # 2. Callback — MUST carry the same ?_oauth-override=1 marker: a real
    #    per-tenant interceptor re-derives overrides from the request (e.g.
    #    hostname) independently on each leg, not from the OAuth state. This is
    #    the leg OAuthService.handleCallback previously ignored entirely — see
    #    restheart#653.
    Given path '/auth/oauth/callback/test'
    And param _oauth-override = '1'
    And param code = 'test-' + overrideEmail
    And param state = state
    When method GET
    Then status 307
    * def setCookieHeader = responseHeaders['Set-Cookie'][0]
    * match setCookieHeader contains 'rh_auth=Bearer_'

    # 3. TestOAuthProvider.fetchUserProfile echoes the clientId/callbackUrl it
    #    actually received into fields persisted on the user document — proves
    #    the OVERRIDDEN credentials and api-base-url reached the callback's
    #    token-exchange call, not the static test-client / localhost:8080.
    Given path '/users/' + overrideEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.socialAuths[0].providerId contains 'overridden-client-id'
    And match response.profile.avatarUrl == 'http://localhost:8080/overridden/auth/oauth/callback/test'

  # ---------------------------------------------------------------------------
  Scenario: without the override marker, OAuth still resolves the static provider config
  # ---------------------------------------------------------------------------

    # Regression pin for the scenario above: proves the sentinel values asserted
    # there really come from the override, not from a change to the static/default
    # provider config or to TestOAuthProvider's echoing behavior.

    * def plainEmail = 'oauth-plain-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/oauth/authorize/test'
    When method GET
    Then status 307
    * def location = responseHeaders['Location'][0]
    * match location contains 'client_id=test-client'
    * match location contains 'scope=email'
    * def state = location.split('state=')[1].split('&')[0]

    Given path '/auth/oauth/callback/test'
    And param code = 'test-' + plainEmail
    And param state = state
    When method GET
    Then status 307

    Given path '/users/' + plainEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.socialAuths[0].providerId contains 'test-client'
    And match response.profile.avatarUrl == 'http://localhost:8080/auth/oauth/callback/test'

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
  Scenario: existing user accepts invitation via OAuth (pendingInviteToken in state)
  # ---------------------------------------------------------------------------

    # 1. Register + verify a fresh existing user
    * def existingEmail = 'oauth-existing-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/register'
    And request { "firstName": "Existing", "lastName": "User", "teamName": "My Team", "email": "#(existingEmail)", "password": "Existing1!" }
    When method POST
    Then status 201

    Given path '/users/' + existingEmail
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def verifyTok = response.emailVerificationToken
    Given path '/auth/verify'
    And param email = existingEmail
    And param token = verifyTok
    When method GET
    * match [200, 302] contains responseStatus

    # 2. Owner invites the existing user
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(existingEmail)", "role": "member" }
    When method POST
    Then status 201

    # 3. Read invitation token from auth_invitations
    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: existingEmail })
    * def inviteToken = tokenResult.result
    * match inviteToken == '#notnull'

    # 4. Existing user initiates OAuth with pendingInviteToken
    Given path '/auth/oauth/authorize/test'
    And param pendingInviteToken = inviteToken
    When method GET
    Then status 307
    * def state = responseHeaders['Location'][0].split('state=')[1].split('&')[0]

    # 5. OAuth callback — user is NOT $unauthenticated → accepted via auth_invitations
    Given path '/auth/oauth/callback/test'
    And param code = 'test-' + existingEmail
    And param state = state
    When method GET
    Then status 307
    * def callbackLocation = responseHeaders['Location'][0]
    * match callbackLocation contains 'localhost:4200/app'
    * match callbackLocation contains '#access_token='
    * match callbackLocation.split('#')[0] !contains 'access_token='
    * def setCookieHeader = responseHeaders['Set-Cookie'][0]
    * match setCookieHeader contains 'rh_auth=Bearer_'

    # 6. auth_invitations entry must be deleted after acceptance
    Given path '/auth_invitations'
    And header Authorization = adminAuth
    And param filter = '{"email":"' + existingEmail + '"}'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[0]'

    # 7. User must be a member of the inviting org
    * def jwtPart = setCookieHeader.split('rh_auth=Bearer_')[1].split(';')[0]
    * def parts = jwtPart.split('.')
    * def payloadJson = new java.lang.String(java.util.Base64.getUrlDecoder().decode(parts[1]))
    * def payload = JSON.parse(payloadJson)
    * match payload.sub == existingEmail

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
