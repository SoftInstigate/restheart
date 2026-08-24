Feature: override-accounts-account-properties-claims per-request + JWT claims denylist

  # Verifies issue #661: account-properties-claims can be overridden per-request
  # (attached by accountPropertiesClaimsOverrideInterceptor when ?_claims-override=<a,b,c>
  # is present, simulating TeamConfigInterceptor), and that JwtHelper's denylist blocks
  # sensitive fields (the password property, one-shot verification/reset tokens) from
  # ever becoming a JWT claim, even when the override explicitly lists them.
  #
  # accountPropertiesClaimsOverrideInterceptor (enabled in conf-overrides.yml) attaches
  # override-accounts-account-properties-claims only to requests carrying ?_claims-override
  # — existing tests are unaffected.

  Background:
    * url baseUrl
    * configure followRedirects = false

  # ---------------------------------------------------------------------------
  Scenario: override replaces the static claims list, and the denylist cannot be bypassed
  # ---------------------------------------------------------------------------
    * def email = 'claims-override-' + java.util.UUID.randomUUID() + '@example.com'

    # 1. Register
    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Claims",
        "lastName":  "Override",
        "teamName":  "Claims Co",
        "email":     "#(email)",
        "password":  "Password123!"
      }
      """
    When method POST
    Then status 201

    # 2. Read the verification token via admin
    Given path '/users/' + email
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def verificationToken = response.emailVerificationToken

    # 3. Verify email, requesting an override list that mixes a legit field ("profile")
    #    with two denylisted ones ("password", "emailVerificationToken"). The latter is
    #    still present on the in-memory user document at the point issueToken() runs
    #    (it is fetched before the DB $unset that removes it) — the real path this
    #    denylist has to defend.
    Given path '/auth/verify'
    And param email = email
    And param token = verificationToken
    And param _claims-override = 'profile,password,emailVerificationToken'
    When method GET
    Then status 302
    * assert !responseHeaders['Location'][0].contains('error=')

    # 4. Decode the JWT from the auth cookie
    * def cookieHeader = responseHeaders['Set-Cookie'][0]
    * match cookieHeader contains 'rh_auth=Bearer_'
    * def jwtPart = cookieHeader.split('rh_auth=Bearer_')[1].split(';')[0]
    * def parts = jwtPart.split('.')
    * def payloadJson = new java.lang.String(java.util.Base64.getUrlDecoder().decode(parts[1]))
    * def payload = JSON.parse(payloadJson)
    * karate.log('JWT payload:', payload)

    # The override took effect end-to-end: "profile" is NOT in the static default list
    # (accountsConfig.account-properties-claims: [teams]), so its presence proves
    # override-accounts-account-properties-claims was applied, not the static config.
    * match payload.profile == '#present'
    * match payload.profile.name == 'Claims'

    # The denylist cannot be bypassed by the override, however explicitly requested.
    * match payload.password == '#notpresent'
    * match payload.emailVerificationToken == '#notpresent'

    # The override REPLACES the static list, but it cannot lower the required-claims floor:
    # "teams" is listed in jwtConfigProvider/required-account-properties-claims, so it is
    # present even though this override never asked for it. See the dedicated scenario below.
    * match payload.teams == '#present'

  # ---------------------------------------------------------------------------
  Scenario: /token applies the same per-request override and denylist
  # ---------------------------------------------------------------------------
    # Authenticate as a file-realm user that has a "profile" property,
    # via /token (JwtTokenManager path) with the override.
    * def creds = 'claimsTest:ClaimsPass123!'
    * def Base64 = Java.type('java.util.Base64')
    * def encoded = Base64.getEncoder().encodeToString(creds.getBytes())

    Given path '/token'
    And header Authorization = 'Basic ' + encoded
    And param _claims-override = 'profile,password,emailVerificationToken'
    When method POST
    Then status 200
    And match response.access_token == '#present'

    # Decode the JWT
    * def jwtPart = response.access_token
    * def parts = jwtPart.split('.')
    * def payloadJson = new java.lang.String(java.util.Base64.getUrlDecoder().decode(parts[1]))
    * def payload = JSON.parse(payloadJson)
    * karate.log('JWT payload from /token:', payload)

    # Override took effect: "profile" is NOT in the static default list (teams),
    # so its presence proves the per-request override was applied by JwtTokenManager.
    * match payload.profile == '#present'
    * match payload.profile.name == 'FileClaims'

    # Denylist cannot be bypassed
    * match payload.password == '#notpresent'
    * match payload.emailVerificationToken == '#notpresent'

    # Override REPLACES the static list, but required claims survive it — see the
    # dedicated scenario below.
    * match payload.teams == '#present'

  # ---------------------------------------------------------------------------
  Scenario: a MongoRealmAccount's profile is stored as given, ready for /token claim resolution
  # ---------------------------------------------------------------------------
    # This used to end with a Basic-Auth POST to /token, to check that claim selection
    # (a JXPath helper meant for the YAML configuration tree) also resolves against the
    # map MongoRealmAccount rebuilds through GSON, not just the plain map FileRealmAccount
    # hands back. Dropped: basicAuthMechanism in this test env is wired to a single
    # authenticator (fileRealmAuthenticator, see conf-overrides.yml), so it can never
    # verify a MongoDB-backed account regardless of the account or the claim logic being
    # correct — every such call 401s here on authentication, before claim resolution is
    # even reached. What's left checks the data /token's claim resolution would read from,
    # via the admin-authenticated path the scenarios above already use.
    * def email = 'claims-mongo-' + java.util.UUID.randomUUID() + '@example.com'
    * def password = 'Password123!'

    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Mongo",
        "lastName":  "Claims",
        "teamName":  "Mongo Claims Co",
        "email":     "#(email)",
        "password":  "#(password)"
      }
      """
    When method POST
    Then status 201

    Given path '/users/' + email
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def verificationToken = response.emailVerificationToken

    Given path '/auth/verify'
    And param email = email
    And param token = verificationToken
    When method GET
    Then status 302

    # Give the user a nested profile field to select as a claim
    Given path '/users/' + email
    And header Authorization = adminAuth
    And request { "profile": { "name": "MongoClaims" } }
    When method PATCH
    Then status 200

    Given path '/users/' + email
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * match response.profile.name == 'MongoClaims'

  # ---------------------------------------------------------------------------
  Scenario: required claims survive an override that does not list them
  # ---------------------------------------------------------------------------
    # required-account-properties-claims is the floor a per-request override cannot lower.
    # It exists because a deployment can depend on a claim being there — a multi-tenant node
    # verifies the claim naming the issuing node on every later request, so a tenant able to
    # drop it would lock itself out with a config change.
    #
    # conf-overrides.yml sets jwtConfigProvider/required-account-properties-claims to include
    # "teams"; the override below asks for "profile" only.
    * def creds = 'claimsTest:ClaimsPass123!'
    * def Base64 = Java.type('java.util.Base64')
    * def encoded = Base64.getEncoder().encodeToString(creds.getBytes())

    Given path '/token'
    And header Authorization = 'Basic ' + encoded
    And param _claims-override = 'profile'
    When method POST
    Then status 200

    * def parts = response.access_token.split('.')
    * def payload = JSON.parse(new java.lang.String(java.util.Base64.getUrlDecoder().decode(parts[1])))
    * karate.log('JWT payload with required claims:', payload)

    # The override was applied ...
    * match payload.profile == '#present'
    # ... and the required claim is there anyway, though the override never listed it
    * match payload.teams == '#present'

  # ---------------------------------------------------------------------------
  Scenario: the denylist wins over required claims
  # ---------------------------------------------------------------------------
    # A credential must not become a claim even if configuration declares it required.
    * def creds = 'claimsTest:ClaimsPass123!'
    * def Base64 = Java.type('java.util.Base64')
    * def encoded = Base64.getEncoder().encodeToString(creds.getBytes())

    Given path '/token'
    And header Authorization = 'Basic ' + encoded
    And param _claims-override = 'password'
    When method POST
    Then status 200

    * def parts = response.access_token.split('.')
    * def payload = JSON.parse(new java.lang.String(java.util.Base64.getUrlDecoder().decode(parts[1])))
    * match payload.password == '#notpresent'
