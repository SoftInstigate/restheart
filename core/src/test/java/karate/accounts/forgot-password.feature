Feature: POST /auth/forgot-password

  # Tests for the password-reset initiation endpoint.
  # Anti-enumeration: always returns 202 regardless of whether the email exists.
  # X-Skip-Email: true is set globally in karate-config.js.
  #
  # Scenarios that need an active user perform a full register+verify setup inline.
  # followRedirects is disabled because the setup calls GET /auth/verify (302).

  Background:
    * url baseUrl
    * configure followRedirects = false

  # ---------------------------------------------------------------------------
  Scenario: existing active email — always returns 202
  # ---------------------------------------------------------------------------
    * def email = 'forgot-active-' + java.util.UUID.randomUUID() + '@example.com'

    # Setup: register
    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Test",
        "lastName":  "User",
        "teamName":  "Test Corp",
        "email":     "#(email)",
        "password":  "Password123!"
      }
      """
    When method POST
    Then status 201

    # Setup: read verification token from MongoDB
    Given path '/users/' + email
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def verifyToken = response.emailVerificationToken

    # Setup: verify email to activate the account
    Given path '/auth/verify'
    And param email = email
    And param token = verifyToken
    When method GET
    Then status 302

    # Actual test: forgot-password for an active user
    Given path '/auth/forgot-password'
    And request { "email": "#(email)" }
    When method POST
    Then status 202

  # ---------------------------------------------------------------------------
  Scenario: unverified user — returns 202, gets a reset token, and the reset verifies the account
  # ---------------------------------------------------------------------------
    # Anyone can register any address, so the password given at registration may not be
    # the owner's. The owner recovers the account through the reset link, which makes them
    # choose the password and verifies the address in one step (GHSA-m7m7-gjxg-cjmm).
    # Before 9.13.0 the verification link was re-sent and the reset was refused.
    * def email = 'forgot-pending-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Pending",
        "lastName":  "User",
        "teamName":  "Pending Corp",
        "email":     "#(email)",
        "password":  "Seeded-Password1!"
      }
      """
    When method POST
    Then status 201

    # Do NOT verify email — user stays unverified
    Given path '/auth/forgot-password'
    And request { "email": "#(email)" }
    When method POST
    Then status 202

    # The reset token is written, the account is still unverified
    Given path '/users/' + email
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.roles contains '$unauthenticated'
    And match response.passwordResetToken == '#notnull'
    And match response.emailVerificationToken == '#notnull'
    * def resetToken = response.passwordResetToken

    # The reset sets the owner's password and verifies the account
    Given path '/auth/reset-password'
    And request { "email": "#(email)", "token": "#(resetToken)", "password": "Owner-Password1!" }
    When method PATCH
    Then status 200
    * def ownerJwt = responseHeaders['Set-Cookie'][0].split('Bearer_')[1].split(';')[0]

    Given path '/users/' + email
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.roles contains 'user'
    And match response.roles !contains '$unauthenticated'
    And match response.passwordResetToken == '#notpresent'
    And match response.emailVerificationToken == '#notpresent'
    And match response.emailVerificationCreatedAt == '#notpresent'

    # The stored password is the owner's, not the one given at registration. Basic auth maps
    # to the file realm in this test configuration, so the check goes through
    # PATCH /auth/change-password, which bcrypt-verifies currentPassword against the document.
    Given path '/auth/change-password'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "currentPassword": "Seeded-Password1!", "newPassword": "Another-Password1!" }
    When method PATCH
    Then status 401

    Given path '/auth/change-password'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "currentPassword": "Owner-Password1!", "newPassword": "Another-Password1!" }
    When method PATCH
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: non-existent email — always returns 202 (anti-enumeration)
  # ---------------------------------------------------------------------------
    Given path '/auth/forgot-password'
    And request { "email": "does-not-exist@invalid-domain-xyz.example" }
    When method POST
    Then status 202

  # ---------------------------------------------------------------------------
  Scenario: empty body (missing email field) — returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/forgot-password'
    And request {}
    When method POST
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: verify DB — after forgot-password the user document has passwordResetToken set
  # ---------------------------------------------------------------------------
    * def email = 'forgot-db-' + java.util.UUID.randomUUID() + '@example.com'

    # Setup: register
    Given path '/auth/register'
    And request
      """
      {
        "firstName": "DB",
        "lastName":  "Check",
        "teamName":  "DB Corp",
        "email":     "#(email)",
        "password":  "Password123!"
      }
      """
    When method POST
    Then status 201

    # Setup: read and use verification token (activate the account)
    Given path '/users/' + email
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def verifyToken = response.emailVerificationToken

    Given path '/auth/verify'
    And param email = email
    And param token = verifyToken
    When method GET
    Then status 302

    # Trigger forgot-password
    Given path '/auth/forgot-password'
    And request { "email": "#(email)" }
    When method POST
    Then status 202

    # Confirm passwordResetToken was written to the user document
    # (processResetRequest runs synchronously — token is present by the time 202 is received)
    Given path '/users/' + email
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.passwordResetToken == '#notnull'
    And match response.passwordResetCreatedAt == '#notnull'
