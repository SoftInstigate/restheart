Feature: PATCH /auth/activate

  # Tests for the invitation-activation endpoint.
  # X-Skip-Email: true is set globally in karate-config.js.
  #
  # Background (per scenario):
  #   1. callonce: ensures admin@test.example.com exists.
  #   2. Invites a fresh user via the owner, then reads the inviteToken from MongoDB.
  #      Every scenario therefore gets an isolated email + inviteToken pair.

  Background:
    * url baseUrl
    * configure followRedirects = false
    # Ensure owner-test@example.com exists and is active (idempotent)
    * def setupResult = karate.callSingle('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = setupResult.ownerJwt

    # Invite a fresh user for this scenario
    * def inviteEmail = 'activate-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(inviteEmail)", "role": "user" }
    When method POST
    Then status 201

    # Read inviteToken from MongoDB
    Given path '/users/' + inviteEmail
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def inviteToken = response.inviteToken

  # ---------------------------------------------------------------------------
  Scenario: happy path — valid token, password, and consents returns 200 with cookie
  # ---------------------------------------------------------------------------
    Given path '/auth/activate'
    And request
      """
      {
        "email":    "#(inviteEmail)",
        "token":    "#(inviteToken)",
        "password": "Activated1!",
        "consents": { "terms": true, "privacy": true }
      }
      """
    When method PATCH
    Then status 200

    # Auth cookie must be set (auto-login after activation)
    * def cookieHeader = responseHeaders['Set-Cookie'][0]
    * match cookieHeader contains 'rh_auth=Bearer_'
    * match cookieHeader contains 'HttpOnly'

    # Response body confirms activation
    And match response.message == '#notnull'

  # ---------------------------------------------------------------------------
  Scenario: token not found — returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/activate'
    And request
      """
      {
        "email":    "#(inviteEmail)",
        "token":    "0000000011111111222222223333333344444444555555556666666677777777",
        "password": "Activated1!",
        "consents": { "terms": true, "privacy": true }
      }
      """
    When method PATCH
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: email mismatch — valid token but wrong email returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/activate'
    And request
      """
      {
        "email":    "wrong-email@example.com",
        "token":    "#(inviteToken)",
        "password": "Activated1!",
        "consents": { "terms": true, "privacy": true }
      }
      """
    When method PATCH
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: missing consents — returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/activate'
    And request
      """
      {
        "email":    "#(inviteEmail)",
        "token":    "#(inviteToken)",
        "password": "Activated1!"
      }
      """
    When method PATCH
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: token already used (one-shot) — second activation returns 401
  # ---------------------------------------------------------------------------
    # First activation — must succeed and remove the token
    Given path '/auth/activate'
    And request
      """
      {
        "email":    "#(inviteEmail)",
        "token":    "#(inviteToken)",
        "password": "Activated1!",
        "consents": { "terms": true, "privacy": true }
      }
      """
    When method PATCH
    Then status 200

    # Second activation with the same token — must fail (token was removed)
    Given path '/auth/activate'
    And request
      """
      {
        "email":    "#(inviteEmail)",
        "token":    "#(inviteToken)",
        "password": "AnotherPassword2!",
        "consents": { "terms": true, "privacy": true }
      }
      """
    When method PATCH
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: verify DB — after activation user has status=active and inviteToken removed
  # ---------------------------------------------------------------------------
    Given path '/auth/activate'
    And request
      """
      {
        "email":    "#(inviteEmail)",
        "token":    "#(inviteToken)",
        "password": "Activated1!",
        "consents": { "terms": true, "privacy": true }
      }
      """
    When method PATCH
    Then status 200

    # Read user document from MongoDB and verify post-activation state
    Given path '/users/' + inviteEmail
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.status == 'active'
    # inviteToken and inviteCreatedAt must have been $unset
    And match response.inviteToken == '#notpresent'
    And match response.inviteCreatedAt == '#notpresent'
    # Consent records must be stored
    And match response.consents.terms.version == '#notnull'
    And match response.consents.privacy.version == '#notnull'
