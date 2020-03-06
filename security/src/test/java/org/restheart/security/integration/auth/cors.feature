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

Scenario: request proxied and secured service with valid credentianls and check CORS
    Given path '/secho'
    And header Authorization = basic ( {username: 'admin', password: 'secret' } )
    When method GET
    Then status 200
    And match responseHeaders contains { Access-Control-Allow-Credentials: [ 'true' ] } 
    And match responseHeaders contains any  { Access-Control-Allow-Origin: ['*'] }
    And match responseHeaders contains any  { Access-Control-Expose-Headers: ['Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By']}

Scenario: request proxied and secured service with invalid credentianls and check CORS
    Given path '/secho'
    And header Authorization = basic ( {username: 'admin', password: 'wrong!' } )
    When method GET
    Then status 401
    And match responseHeaders contains { Access-Control-Allow-Credentials: [ 'true' ] } 
    And match responseHeaders contains any  { Access-Control-Allow-Origin: ['*'] }
    And match responseHeaders contains any  { Access-Control-Expose-Headers: ['Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By']}

Scenario: request proxied and secured service with valid credentianls but forbidden and check CORS
    Given path '/secho'
    And header Authorization = basic ( {username: 'noroles', password: 'secret' } )
    When method GET
    Then status 403
    And match responseHeaders contains { Access-Control-Allow-Credentials: [ 'true' ] } 
    And match responseHeaders contains any  { Access-Control-Allow-Origin: ['*'] }
    And match responseHeaders contains any  { Access-Control-Expose-Headers: ['Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By']}

Scenario: request service and check CORS
    Given path '/echo'
    When method GET
    Then status 200
    And match responseHeaders contains { Access-Control-Allow-Credentials: [ 'true' ] } 
    And match responseHeaders contains any  { Access-Control-Allow-Origin: ['*'] }
    And match responseHeaders contains any  { Access-Control-Expose-Headers: ['Location, ETag, Auth-Token, Auth-Token-Valid-Until, Auth-Token-Location, X-Powered-By']}
    