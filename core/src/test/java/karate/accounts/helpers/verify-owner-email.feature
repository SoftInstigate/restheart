Feature: verify email for owner-test@example.com

  @helper
  Scenario: call /auth/verify with the given token
    * url baseUrl
    * configure followRedirects = false

    Given path '/auth/verify'
    And param email = 'owner-test@example.com'
    And param token = token
    When method GET
    # 302 = successful redirect to app
    * match [302, 200] contains responseStatus
    * karate.log('Email verified for owner-test@example.com, status:', responseStatus)
