Feature: test /token/redirect endpoint for redirect-based token handoff

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
* def parseFragment =
"""
function(frag) {
  var result = {};
  var pairs = frag.split('&');
  for (var i = 0; i < pairs.length; i++) {
    var kv = pairs[i].split('=');
    result[decodeURIComponent(kv[0])] = decodeURIComponent(kv[1]);
  }
  return result;
}
"""

Scenario: GET /token/redirect with Basic Auth - token in body AND redirect with fragment
    * configure followRedirects = false
    Given path '/token/redirect'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method GET
    Then status 307
    And match response.access_token == '#string'
    And match response.token_type == 'Bearer'
    And match response.username == 'admin'
    And match response.roles == '#array'
    And match responseHeaders['Cache-Control'][0] == 'no-store'
    And def location = responseHeaders['Location'][0]
    And match location contains 'http://localhost:8080/secho#access_token='
    # token must be in the fragment, never the query string
    And def queryPart = location.split('#')[0]
    And assert queryPart.indexOf(response.access_token) === -1

Scenario: end-to-end - extract token from the redirect fragment, use it to authenticate
    * configure followRedirects = false
    Given path '/token/redirect'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method GET
    Then status 307
    And def location = responseHeaders['Location'][0]
    And def fragment = location.split('#')[1]
    And def extracted = parseFragment(fragment)
    And match extracted.access_token == '#string'
    And match extracted.token_type == 'Bearer'

    # the token handed off via the fragment is a real, usable Bearer token
    Given path '/secho'
    And header Authorization = 'Bearer ' + extracted.access_token
    When method GET
    Then status 200

Scenario: GET /token/redirect without authentication - 401
    Given path '/token/redirect'
    When method GET
    Then status 401

Scenario: POST /token/redirect should not be allowed
    Given path '/token/redirect'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method POST
    Then status 405

Scenario: DELETE /token/redirect should not be allowed
    Given path '/token/redirect'
    And header Authorization = basic({username: 'admin', password: 'secret'})
    When method DELETE
    Then status 405
