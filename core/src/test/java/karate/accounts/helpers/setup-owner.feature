Feature: setup owner user for invite and activate tests

  # Ensures owner-test@example.com is registered and active.
  # Idempotent: works whether the user already exists or not.
  # Call with: * callonce read('classpath:karate/accounts/helpers/setup-owner.feature')

  @helper
  Scenario: register and activate owner-test@example.com
    * url baseUrl
    * configure followRedirects = false

    # 1. Register — 201 if new, 409 if already exists
    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Owner",
        "lastName":  "Test",
        "teamName":  "Owner Test Team",
        "email":     "owner-test@example.com",
        "password":  "OwnerPass123!"
      }
      """
    When method POST

    # 2. Fetch the user document to check current state
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def verifyToken = response.emailVerificationToken

    # 3. Verify email only if the token is still present (user not yet active)
    #    Use ternary — karate.call() inside `if` fails in GraalVM JS
    * def setupResult = verifyToken ? karate.call('classpath:karate/accounts/helpers/verify-owner-email.feature', { token: verifyToken }) : null

    * karate.log('Owner setup complete: owner-test@example.com')
