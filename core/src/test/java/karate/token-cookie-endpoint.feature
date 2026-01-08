Feature: test /token/cookie endpoint for cookie-based authentication

Background:
* url 'http://localhost:8080'
* def basic =
"""
function(creds) {
  var temp = creds.username + ':' + creds.password;
  var Base64 = Java.type('java.util.Base64');
  var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
  return 'Basic ' + encoded;
}
"""

Scenario: POST /token/cookie with Basic Auth - get token in cookie, NOT in body
    Given path '/token/cookie'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method POST
    Then status 200
    # Should have authenticated flag but NO access_token in body (security)
    And match response.authenticated == true
    And match response.username == 'admin'
    And match response.roles == '#array'
    And match response.access_token == '#notpresent'
    # Token should be in Set-Cookie header
    And match responseHeaders['Set-Cookie'][0] contains 'rh_auth='
    And match responseHeaders['Set-Cookie'][0] contains 'HttpOnly'
    And match responseHeaders['Set-Cookie'][0] contains 'SameSite=Strict'
    And match responseHeaders['Cache-Control'][0] == 'no-store'

Scenario: POST /token/cookie with OAuth 2.0 form data - get token in cookie
    Given path '/token/cookie'
    And form field grant_type = 'password'
    And form field username = 'admin'
    And form field password = 'secret'
    When method POST
    Then status 200
    And match response.authenticated == true
    And match response.username == 'admin'
    And match response.access_token == '#notpresent'
    And match responseHeaders['Set-Cookie'][0] contains 'rh_auth='

Scenario: POST /token/cookie, then use cookie to access protected resource
    # Get cookie
    Given path '/token/cookie'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method POST
    Then status 200
    And def cookie = responseHeaders['Set-Cookie'][0]

    # Use cookie to access protected resource
    Given path '/secho'
    And header Cookie = cookie
    When method GET
    Then status 200

Scenario: POST /token/cookie?renew=true - renew cookie
    # First, get a cookie
    Given path '/token/cookie'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method POST
    Then status 200
    And def firstCookie = responseHeaders['Set-Cookie'][0]

    # Renew the cookie
    Given path '/token/cookie'
    And param renew = 'true'
    And header Cookie = firstCookie
    When method POST
    Then status 200
    And def renewedCookie = responseHeaders['Set-Cookie'][0]
    # Should get a new cookie (different token)
    And assert renewedCookie != firstCookie

Scenario: GET /token/cookie should not be allowed
    Given path '/token/cookie'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method GET
    Then status 405

Scenario: DELETE /token/cookie should not be allowed (use /logout instead)
    Given path '/token/cookie'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method DELETE
    Then status 405

Scenario: POST /token/cookie without authentication - should fail
    Given path '/token/cookie'
    When method POST
    Then status 401
