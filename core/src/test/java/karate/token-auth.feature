Feature: test basic authentication mechanism

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
# YWRtaW46Y2hhbmdlaXQ= => admin:secret

Scenario: use password, get token from /token endpoint and use it
    # Get token from /token endpoint
    Given path '/token'
    And header Authorization = basic ( {username: 'admin', password: 'secret' } )
    When method GET
    Then status 200
    And def token = responseHeaders['Auth-Token'][0]

    # Use the token to authenticate to another endpoint
    Given path '/secho'
    And def creds = {username: 'admin', password: '#(token)' }
    And header Authorization = basic( creds )
    When method GET
    Then status 200

Scenario: use password, get token and use it. then use password again and the token got from first request
    # Get token from /token endpoint
    Given path '/token'
    And header Authorization = basic ( {username: 'admin', password: 'secret' } )
    When method GET
    Then status 200
    And def token = responseHeaders['Auth-Token'][0]

    # Use the token
    Given path '/secho'
    And def creds = {username: 'admin', password: '#(token)' }
    And header Authorization = basic( creds )
    When method GET
    Then status 200

    # Get token again using password
    Given path '/token'
    And header Authorization = basic ( {username: 'admin', password: 'secret' } )
    When method GET
    Then status 200

    # Use the original token again (should still work, cached)
    Given path '/secho'
    And header Authorization = basic( creds )
    When method GET
    Then status 200
