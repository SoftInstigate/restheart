Feature: test OAuth 2.1 Authorization Code + PKCE flow

Background:
* url 'http://localhost:8080'
* configure followRedirects = false
* def codeVerifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'
* def computeCodeChallenge =
"""
function(verifier) {
  var MessageDigest = Java.type('java.security.MessageDigest');
  var Base64 = Java.type('java.util.Base64');
  var digest = MessageDigest.getInstance('SHA-256');
  var hashBytes = digest.digest(verifier.getBytes('US-ASCII'));
  return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
}
"""
* def codeChallenge = computeCodeChallenge(codeVerifier)
* def extractQueryParam =
"""
function(url, param) {
  var idx = url.indexOf(param + '=');
  if (idx < 0) return null;
  var start = idx + param.length + 1;
  var end = url.indexOf('&', start);
  var value = end < 0 ? url.substring(start) : url.substring(start, end);
  return decodeURIComponent(value);
}
"""
* def basic =
"""
function(creds) {
  var temp = creds.username + ':' + creds.password;
  var Base64 = Java.type('java.util.Base64');
  var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
  return 'Basic ' + encoded;
}
"""

Scenario: GET /authorize without code_challenge - should return 400
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    When method GET
    Then status 400

Scenario: GET /authorize with valid PKCE params - should redirect to login URL
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param state = 'test-state'
    When method GET
    Then status 302
    And match responseHeaders['Location'][0] contains 'http://localhost:3000/login'
    And match responseHeaders['Location'][0] contains 'response_type=code'
    And match responseHeaders['Location'][0] contains 'code_challenge='

Scenario: POST /authorize without authentication - should return 401
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    When method POST
    Then status 401

Scenario: Full PKCE flow - POST /authorize then POST /token
    # Step 1: POST /authorize with valid credentials → get authorization code
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param state = 'test-state-123'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method POST
    Then status 302
    And def location = responseHeaders['Location'][0]
    And match location contains 'http://localhost:3000/callback'
    And match location contains 'code='
    And match location contains 'state=test-state-123'
    And def authCode = extractQueryParam(location, 'code')

    # Step 2: POST /token with authorization_code grant → get access token
    Given path '/token'
    And form field grant_type = 'authorization_code'
    And form field code = authCode
    And form field redirect_uri = 'http://localhost:3000/callback'
    And form field client_id = 'test-client'
    And form field code_verifier = codeVerifier
    When method POST
    Then status 200
    And match response.access_token == '#present'
    And match response.token_type == 'Bearer'
    And match response.expires_in == '#number'
    And match responseHeaders['Cache-Control'][0] == 'no-store'

Scenario: POST /token with invalid authorization code - should return 400
    Given path '/token'
    And form field grant_type = 'authorization_code'
    And form field code = 'invalid.jwt.code'
    And form field redirect_uri = 'http://localhost:3000/callback'
    And form field client_id = 'test-client'
    And form field code_verifier = codeVerifier
    When method POST
    Then status 400
    And match response.error == 'invalid_grant'

Scenario: POST /token with wrong code_verifier - should return 400
    # Get a valid auth code first
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method POST
    Then status 302
    And def location = responseHeaders['Location'][0]
    And def authCode = extractQueryParam(location, 'code')

    # Exchange with wrong code_verifier → PKCE check fails
    Given path '/token'
    And form field grant_type = 'authorization_code'
    And form field code = authCode
    And form field redirect_uri = 'http://localhost:3000/callback'
    And form field client_id = 'test-client'
    And form field code_verifier = 'wrong-code-verifier-that-does-not-match'
    When method POST
    Then status 400
    And match response.error == 'invalid_grant'

Scenario: POST /token with missing code - should return 400
    Given path '/token'
    And form field grant_type = 'authorization_code'
    And form field redirect_uri = 'http://localhost:3000/callback'
    And form field client_id = 'test-client'
    And form field code_verifier = codeVerifier
    When method POST
    Then status 400
    And match response.error == 'invalid_request'

Scenario: POST /authorize with form body credentials (valid) - should redirect to callback with code
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param state = 'form-state-42'
    And header Content-Type = 'application/x-www-form-urlencoded'
    And request 'username=admin&password=secret'
    When method POST
    Then status 302
    And def location = responseHeaders['Location'][0]
    And match location contains 'http://localhost:3000/callback'
    And match location contains 'code='
    And match location contains 'state=form-state-42'

Scenario: POST /authorize with form body credentials (invalid) - should redirect to login with error
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param state = 'form-state-bad'
    And header Content-Type = 'application/x-www-form-urlencoded'
    And request 'username=admin&password=wrongpassword'
    When method POST
    Then status 302
    And def location = responseHeaders['Location'][0]
    And match location contains 'http://localhost:3000/login'
    And match location contains 'error=invalid_credentials'
