Feature: verify email for second-team-owner@example.com

  @helper
  Scenario: call /auth/verify with the given token
    * url baseUrl
    * configure followRedirects = false

    Given path '/auth/verify'
    And param email = 'second-team-owner@example.com'
    And param token = token
    When method GET
    * match [302, 200] contains responseStatus
    * karate.log('Email verified for second-team-owner@example.com, status:', responseStatus)

    # Capture JWT from Set-Cookie header
    * def setCookieList = responseHeaders['Set-Cookie']
    * def setCookie = setCookieList != null && setCookieList.length > 0 ? setCookieList[0] : ''
    * def secondOwnerJwt = setCookie.indexOf('Bearer_') >= 0 ? setCookie.split('Bearer_')[1].split(';')[0] : ''
