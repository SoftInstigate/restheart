Feature: test basic authentication mechanism

Background:
* url 'http://localhost:8080'
* def basic =
""" 
function(creds) {
  var temp = creds.username + ':' + creds.password;
  var Base64 = Java.type('java.util.Base64');
  var encoded = Base64.getEncoder().encodeToString(temp.bytes);
  return 'Basic ' + encoded;
}
"""
* def authHeader = basic({username: 'admin', password: 'secret' })
* def wrongAuthHeader = basic({username: 'admin', password: 'wrong!' })
* def basicAuthChallenge = 'Basic realm="RESTHeart Realm"'

Scenario: request without Authorization header
    * header Accept-Encoding = 'identity'
    Given path '/secho'
    When method GET
    Then status 401
    * def challenge = responseHeaders['WWW-Authenticate'][0]
    And match challenge == basicAuthChallenge

Scenario: request with wrong Authorization header
    * header Authorization = wrongAuthHeader
    Given path '/secho'
    When method GET
    Then status 401
    * def challenge = responseHeaders['WWW-Authenticate'][0]
    And match challenge == basicAuthChallenge

Scenario: request with valid Authorization header
    * header Authorization = authHeader
    Given path '/pecho'    
    When method GET
    Then status 200
    And match response.headers['X-Forwarded-Account-Roles'][0] == 'user'
    And match response.headers['X-Forwarded-Account-Roles'][1] == 'admin'

Scenario: X-Forwarded headers must be filtered out from proxied request
    * header Authorization = authHeader
    * header X-Forwarded-Account-Id = 'anId'
    * header X-Forwarded-Account-Roles = 'aRole'
    * header X-Forwarded-Not-Sensitive-Header = 'aValue'
    Given path '/pecho'    
    When method GET
    Then status 200
    And match response.headers['X-Forwarded-Account-Id'] contains 'admin'
    And match response.headers['X-Forwarded-Account-Id'] !contains 'anId'
    And match response.headers['X-Forwarded-Account-Roles'] contains 'admin'
    And match response.headers['X-Forwarded-Account-Roles'] contains 'user'
    And match response.headers['X-Forwarded-Account-Roles'] !contains 'aRole'
    And match response.headers['X-Forwarded-Not-Sensitive-Header'] contains 'aValue'