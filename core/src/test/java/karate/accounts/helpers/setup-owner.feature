Feature: setup owner user for invite and activate tests

  # Ensures owner-test@example.com is registered and active.
  # Deletes the user first (if present) so each run starts fresh on any DB.
  # After this feature runs, `ownerJwt` is available as a JWT string
  # that callers can use as: header Authorization = 'Bearer ' + ownerJwt

  @helper
  Scenario: register and activate owner-test@example.com
    * url baseUrl
    * configure followRedirects = false
    * def ownerJwt = ''

    # 0. Cleanup — delete owner user if it exists from a previous run
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    When method DELETE
    # 200/204 = deleted, 404 = did not exist — both are fine
    * match [200, 204, 404] contains responseStatus

    # 1. Register fresh
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
    Then status 201

    # 2. Fetch the emailVerificationToken
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def verifyToken = response.emailVerificationToken

    # 3. Verify email → sets status active, issues JWT cookie
    * def verifyResult = karate.call('classpath:karate/accounts/helpers/verify-owner-email.feature', { token: verifyToken })
    * def ownerJwt = verifyResult.ownerJwt

    * karate.log('Owner setup complete: owner-test@example.com, jwt present:', ownerJwt != '')
