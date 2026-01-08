Feature: test /token endpoint with OAuth 2.0 compatibility

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

Scenario: POST /token with Basic Auth - get token in response body
    Given path '/token'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method POST
    Then status 200
    And match response.access_token == '#present'
    And match response.token_type == 'Bearer'
    And match response.expires_in == '#number'
    And match response.username == 'admin'
    And match response.roles == '#array'
    And match responseHeaders['Auth-Token'][0] == '#present'
    And match responseHeaders['Auth-Token-Valid-Until'][0] == '#present'
    And match responseHeaders['Cache-Control'][0] == 'no-store'

Scenario: POST /token with OAuth 2.0 form data - get token in response body
    Given path '/token'
    And form field grant_type = 'password'
    And form field username = 'admin'
    And form field password = 'secret'
    When method POST
    Then status 200
    And match response.access_token == '#present'
    And match response.token_type == 'Bearer'
    And match response.expires_in == '#number'
    And match response.username == 'admin'
    And match response.roles == '#array'
    And match responseHeaders['Auth-Token'][0] == '#present'
    And match responseHeaders['Cache-Control'][0] == 'no-store'

Scenario: POST /token, then GET /token with JWT - should return same token (cache test)
    # First, get a token via POST
    Given path '/token'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method POST
    Then status 200
    And def firstToken = response.access_token
    And def firstExpiry = response.expires_in

    # Now GET with that token - should return the SAME token
    Given path '/token'
    And header Authorization = 'Bearer ' + firstToken
    When method GET
    Then status 200
    And match response.access_token == firstToken
    And match response.token_type == 'Bearer'
    # Expiry should be less (time has passed)
    And assert response.expires_in <= firstExpiry

    # GET again - still should be the same token
    Given path '/token'
    And header Authorization = 'Bearer ' + firstToken
    When method GET
    Then status 200
    And match response.access_token == firstToken

Scenario: GET /token?renew=true - should return a NEW token
    # First, get a token
    Given path '/token'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method POST
    Then status 200
    And def firstToken = response.access_token

    # Sleep 1 second to ensure different exp timestamp
    * def sleep = function(ms){ java.lang.Thread.sleep(ms) }
    * call sleep 1000

    # Now GET with ?renew=true - should get a DIFFERENT token
    Given path '/token'
    And param renew = 'true'
    And header Authorization = 'Bearer ' + firstToken
    When method GET
    Then status 200
    And match response.access_token != firstToken
    And match response.token_type == 'Bearer'
    And def renewedToken = response.access_token

    # GET without renew - should get the renewed token (now cached)
    Given path '/token'
    And header Authorization = 'Bearer ' + renewedToken
    When method GET
    Then status 200
    And match response.access_token == renewedToken

Scenario: POST /token?renew=true with existing JWT - should return a NEW token
    # First, get a token
    Given path '/token'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method POST
    Then status 200
    And def firstToken = response.access_token

    # Sleep 1 second to ensure different exp timestamp
    * def sleep = function(ms){ java.lang.Thread.sleep(ms) }
    * call sleep 1000

    # Now POST with ?renew=true - should get a DIFFERENT token
    Given path '/token'
    And param renew = 'true'
    And header Authorization = 'Bearer ' + firstToken
    When method POST
    Then status 200
    And match response.access_token != firstToken
    And match response.token_type == 'Bearer'

Scenario: POST /token without authentication - should fail
    Given path '/token'
    When method POST
    Then status 401

Scenario: GET /token without authentication - should fail
    Given path '/token'
    When method GET
    Then status 401
