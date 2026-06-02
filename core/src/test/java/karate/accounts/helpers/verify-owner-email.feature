Feature: verify email for owner-test@example.com

  @helper
  Scenario: call /auth/verify with the given token
    * url baseUrl
    * configure followRedirects = false

    Given path '/auth/verify'
    And param email = 'owner-test@example.com'
    And param token = token
    When method GET
    * match [302, 200] contains responseStatus
    * karate.log('Email verified for owner-test@example.com, status:', responseStatus)

    # Capture JWT from Set-Cookie header so callers can use it as Bearer auth
    * def setCookie = responseHeaders['Set-Cookie'] != null ? responseHeaders['Set-Cookie'][0] : ''
    * def ownerJwt = setCookie.indexOf('Bearer_') >= 0 ? setCookie.split('Bearer_')[1].split(';')[0] : ''
