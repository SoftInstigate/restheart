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

Scenario: an unauthenticated /mcp request is challenged with Bearer and points at its metadata

    # An MCP client starts the OAuth flow from this header: a Bearer challenge carrying the
    # RFC 9728 pointer for the resource it just tried to reach. Without it the only challenge is
    # Basic, which the client has no use for, so it never discovers the authorization server.
    Given path '/mcp'
    And request { jsonrpc: '2.0', id: 1, method: 'initialize', params: {} }
    When method POST
    Then status 401
    And match responseHeaders['WWW-Authenticate'] contains '#regex .*Bearer resource_metadata=.*'
    And match responseHeaders['WWW-Authenticate'] contains '#regex .*/\\.well-known/oauth-protected-resource/mcp.*'


Scenario: the pointer names the URL the caller used, not the listener's own

    # Behind a TLS-terminating proxy the listener speaks plain HTTP: a pointer built from its own
    # scheme would send the client to an address the service does not answer on.
    * header X-Forwarded-Proto = 'https'
    * header X-Forwarded-Host = 'api.example.com'
    Given path '/mcp'
    And request { jsonrpc: '2.0', id: 1, method: 'initialize', params: {} }
    When method POST
    Then status 401
    And match responseHeaders['WWW-Authenticate'] contains '#regex .*https://api\\.example\\.com/\\.well-known/oauth-protected-resource/mcp.*'


Scenario: the same headers name the resource in the metadata document

    * header X-Forwarded-Proto = 'https'
    * header X-Forwarded-Host = 'api.example.com'
    Given path '/.well-known/oauth-protected-resource/mcp'
    When method GET
    Then status 200
    And match response.resource == 'https://api.example.com/mcp'
    And match response.authorization_servers[0] == 'https://api.example.com'
