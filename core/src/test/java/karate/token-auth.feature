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
# YWRtaW46Y2hhbmdlaXQ= => admin:secret

Scenario: use password, get token and use it
    Given path '/secho'
    And header Authorization = basic ( {username: 'admin', password: 'secret' } )
    When method GET
    Then status 200
    And def token = responseHeaders['Auth-Token'][0]

    Given path '/secho'  
    And def creds = {username: 'admin', password: '#(token)' }
    And header Authorization = basic( creds )
    When method GET
    Then status 200
    And def token = responseHeaders['Auth-Token'][0]

Scenario: use password, get token and use it. then use password again and the token got from first request
    Given path '/secho'
    And header Authorization = basic ( {username: 'admin', password: 'secret' } )
    When method GET
    Then status 200
    And def token = responseHeaders['Auth-Token'][0]

    Given path '/secho'  
    And def creds = {username: 'admin', password: '#(token)' }
    And header Authorization = basic( creds )
    When method GET
    Then status 200
    And def token = responseHeaders['Auth-Token'][0]

    Given path '/secho'
    And header Authorization = basic ( {username: 'admin', password: 'secret' } )
    When method GET
    Then status 200

    Given path '/secho'
    And header Authorization = basic( creds )
    When method GET
    Then status 200
    And def token = responseHeaders['Auth-Token'][0]