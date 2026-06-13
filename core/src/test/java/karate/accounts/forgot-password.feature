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
