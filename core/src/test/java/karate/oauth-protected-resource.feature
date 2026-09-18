Feature: test OAuth 2.0 Protected Resource Metadata endpoint (RFC 9728)

Background:
* url 'http://localhost:8080'

Scenario: GET /.well-known/oauth-protected-resource - returns metadata without authentication
    Given path '/.well-known/oauth-protected-resource'
    When method GET
    Then status 200
    And match response.resource == '#present'
    And match response.authorization_servers == '#array'
    And match response.authorization_servers[0] == '#present'
    # the instance names itself by what the request came in on, when the operator configured no
    # server-url: the same answer /mcp gives for its own resource URIs, from one place
    And match response.authorization_servers[0] == 'http://localhost:8080'

Scenario: GET /.well-known/oauth-protected-resource - resource field contains server URL
    Given path '/.well-known/oauth-protected-resource'
    When method GET
    Then status 200
    And match response.resource == 'http://localhost:8080'

Scenario: GET /.well-known/oauth-protected-resource/api/v1 - resource path suffix is reflected
    Given path '/.well-known/oauth-protected-resource/api/v1'
    When method GET
    Then status 200
    And match response.resource == 'http://localhost:8080/api/v1'
    And match response.authorization_servers[0] == 'http://localhost:8080'

Scenario: POST /.well-known/oauth-protected-resource - method not allowed
    Given path '/.well-known/oauth-protected-resource'
    When method POST
    Then status 405
