Feature: test OAuth 2.0 Authorization Server Metadata endpoint (RFC 8414)

Background:
* url 'http://localhost:8080'

Scenario: GET /.well-known/oauth-authorization-server - returns metadata without authentication
    Given path '/.well-known/oauth-authorization-server'
    When method GET
    Then status 200
    And match response.issuer == '#present'
    And match response.authorization_endpoint == '#present'
    And match response.token_endpoint == '#present'
    And match response.token_endpoint_auth_methods_supported == '#array'
    And match response.grant_types_supported contains 'password'
    And match response.grant_types_supported contains 'client_credentials'
    And match response.grant_types_supported contains 'authorization_code'
    And match response.response_types_supported contains 'code'
    And match response.code_challenge_methods_supported contains 'S256'

Scenario: GET /.well-known/oauth-authorization-server - endpoints point to correct URIs
    Given path '/.well-known/oauth-authorization-server'
    When method GET
    Then status 200
    And match response.token_endpoint contains '/token'
    And match response.authorization_endpoint contains '/authorize'

Scenario: POST /.well-known/oauth-authorization-server - method not allowed
    Given path '/.well-known/oauth-authorization-server'
    When method POST
    Then status 405
