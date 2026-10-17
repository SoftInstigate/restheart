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

# --- no-auth-challenge suppression (bug fix) ---

Scenario: POST /token/cookie with No-Auth-Challenge header - 401 must NOT contain WWW-Authenticate
    Given path '/token/cookie'
    And header No-Auth-Challenge = 'true'
    When method POST
    Then status 401
    And match responseHeaders['WWW-Authenticate'] == '#notpresent'

Scenario: POST /token/cookie with noauthchallenge query param - 401 must NOT contain WWW-Authenticate
    Given path '/token/cookie'
    And param noauthchallenge = 'true'
    When method POST
    Then status 401
    And match responseHeaders['WWW-Authenticate'] == '#notpresent'

Scenario: the cookie is renewed by presenting it, with no token in the body
    # The reason grant_type=refresh_token also lives on /token/cookie. A browser app cannot put its
    # token in a form field — the cookie holding it is HttpOnly, which is the whole point — so
    # without this the grace window is unreachable by exactly the clients that meet an expired
    # token most often.
    Given path '/token/cookie'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method POST
    Then status 200
    * def setCookie = responseHeaders['Set-Cookie'][0]
    * def issued = setCookie.substring(setCookie.indexOf('Bearer_') + 7, setCookie.indexOf(';'))

    # Renew by sending the cookie, exactly as a browser does: no form field, no password.
    Given path '/token/cookie'
    And header Cookie = 'rh_auth=Bearer_' + issued
    And form field grant_type = 'refresh_token'
    When method POST
    Then status 200
    And match response.authenticated == true
    And match response.username == 'admin'
    # Still never in the body, renewal or not
    And match response.access_token == '#notpresent'
    And match response.refresh_token == '#notpresent'
    And match responseHeaders['Set-Cookie'][0] contains 'rh_auth='
    And match responseHeaders['Set-Cookie'][0] contains 'HttpOnly'

Scenario: renewal on /token is refused when nothing is presented
    # The fallback reads the bearer token off the request; with neither form field nor cookie there
    # is nothing to renew, and the error has to say so rather than blaming the form.
    Given path '/token'
    And form field grant_type = 'refresh_token'
    When method POST
    Then status 400
    And match response.error == 'invalid_request'
