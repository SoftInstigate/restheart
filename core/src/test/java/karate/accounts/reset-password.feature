Feature: PATCH /auth/reset-password

  # Tests for the password reset endpoint.
  # X-Skip-Email: true is set globally in karate-config.js.
  #
  # Background performs a full register → verify → forgot-password → read-token
  # setup for each scenario, providing a fresh `email` and `resetToken`.
  # followRedirects is disabled because Background calls GET /auth/verify (302).

  Background:
    * url baseUrl
    * configure followRedirects = false

    # --- 1. Register a fresh user ---
    * def email = 'reset-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Reset",
        "lastName":  "Tester",
        "teamName":  "Reset Corp",
        "email":     "#(email)",
        "password":  "OldPassword1!"
      }
      """
    When method POST
    Then status 201

    # --- 2. Read emailVerificationToken from MongoDB ---
    Given path '/users/' + email
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def verifyToken = response.emailVerificationToken

    # --- 3. Verify email (activates the account) ---
    Given path '/auth/verify'
    And param email = email
    And param token = verifyToken
    When method GET
    Then status 302

    # --- 4. Request a password reset ---
    Given path '/auth/forgot-password'
    And request { "email": "#(email)" }
    When method POST
    Then status 202

    # --- 5. Read passwordResetToken from MongoDB ---
    Given path '/users/' + email
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def resetToken = response.passwordResetToken

  # ---------------------------------------------------------------------------
  Scenario: happy path — valid token and new password returns 200 with auth cookie
  # ---------------------------------------------------------------------------
    Given path '/auth/reset-password'
    And request
      """
      {
        "email":    "#(email)",
        "token":    "#(resetToken)",
        "password": "NewPassword1!"
      }
      """
    When method PATCH
    Then status 200

    # Auth cookie must be set with a fresh JWT
    * def cookieHeader = responseHeaders['Set-Cookie'][0]
    * match cookieHeader contains 'rh_auth=Bearer_'
    * match cookieHeader contains 'HttpOnly'

    # Response body confirms success
    And match response.message == '#notnull'

  # ---------------------------------------------------------------------------
  Scenario: token not found — returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/reset-password'
    And request
      """
      {
        "email":    "#(email)",
        "token":    "aaaa0000bbbb1111cccc2222dddd3333eeee4444ffff5555aaaa0000bbbb1111",
        "password": "NewPassword1!"
      }
      """
    When method PATCH
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: email mismatch — valid token but wrong email returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/reset-password'
    And request
      """
      {
        "email":    "someone-else@example.com",
        "token":    "#(resetToken)",
        "password": "NewPassword1!"
      }
      """
    When method PATCH
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: password too short (< 8 chars) — returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/reset-password'
    And request
      """
      {
        "email":    "#(email)",
        "token":    "#(resetToken)",
        "password": "short"
      }
      """
    When method PATCH
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: token already used (one-shot) — second call with same token returns 401
  # ---------------------------------------------------------------------------
    # First call — must succeed and consume the token
    Given path '/auth/reset-password'
    And request
      """
      {
        "email":    "#(email)",
        "token":    "#(resetToken)",
        "password": "NewPassword1!"
      }
      """
    When method PATCH
    Then status 200

    # Second call with the same token — must fail (token was removed after first use)
    Given path '/auth/reset-password'
    And request
      """
      {
        "email":    "#(email)",
        "token":    "#(resetToken)",
        "password": "AnotherPassword2!"
      }
      """
    When method PATCH
    Then status 401
