Feature: GET /auth/verify

  # Tests for the email verification endpoint.
  # All outcomes are 302 redirects — success goes to frontendAppUrl,
  # errors go to frontendUrl/auth/login?error=<code>.
  # X-Skip-Email: true is set globally in karate-config.js.

  Background:
    * url baseUrl
    # Prevent Karate from following 302 redirects — we need to inspect Location
    * configure followRedirects = false

  # ---------------------------------------------------------------------------
  Scenario: happy path — valid token activates account and sets auth cookie
  # ---------------------------------------------------------------------------
    * def email = 'verify-ok-' + java.util.UUID.randomUUID() + '@example.com'

    # 1. Register the user
    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Alice",
        "lastName":  "Smith",
        "teamName":  "Test Co",
        "email":     "#(email)",
        "password":  "Password123!"
      }
      """
    When method POST
    Then status 201

    # 2. Read emailVerificationToken from MongoDB via admin
    Given path '/users/' + email
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def verificationToken = response.emailVerificationToken

    # 3. Verify email
    Given path '/auth/verify'
    And param email = email
    And param token = verificationToken
    When method GET
    Then status 302

    # Location must point to the app and must NOT contain an error
    * def location = responseHeaders['Location'][0]
    * match location == '#notnull'
    * assert !location.contains('error=')

    # Auth cookie rh_auth must be set with Bearer_ JWT value
    * def cookieHeader = responseHeaders['Set-Cookie'][0]
    * match cookieHeader contains 'rh_auth=Bearer_'
    * match cookieHeader contains 'HttpOnly'

  # ---------------------------------------------------------------------------
  Scenario: random token — redirects with error=invalid_token
  # ---------------------------------------------------------------------------
    Given path '/auth/verify'
    And param email = 'nobody@example.com'
    And param token = '0000000011111111222222223333333344444444555555556666666677777777'
    When method GET
    Then status 302
    * def location = responseHeaders['Location'][0]
    * match location contains 'error=invalid_token'

  # ---------------------------------------------------------------------------
  Scenario: email mismatch — valid token but wrong email redirects with error
  # ---------------------------------------------------------------------------
    * def email = 'verify-mismatch-' + java.util.UUID.randomUUID() + '@example.com'

    # 1. Register
    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Bob",
        "lastName":  "Jones",
        "teamName":  "BJ Inc",
        "email":     "#(email)",
        "password":  "Password123!"
      }
      """
    When method POST
    Then status 201

    # 2. Read token
    Given path '/users/' + email
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def verificationToken = response.emailVerificationToken

    # 3. Call verify with a DIFFERENT email — anti-token-swap check
    Given path '/auth/verify'
    And param email = 'totally-wrong@example.com'
    And param token = verificationToken
    When method GET
    Then status 302
    * def location = responseHeaders['Location'][0]
    * match location contains 'error=invalid_token'

  # ---------------------------------------------------------------------------
  Scenario: missing token parameter — redirects with error=invalid_token
  # ---------------------------------------------------------------------------
    Given path '/auth/verify'
    And param email = 'user@example.com'
    When method GET
    Then status 302
    * def location = responseHeaders['Location'][0]
    * match location contains 'error=invalid_token'
