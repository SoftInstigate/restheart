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

# --- the challenge that points a refused MCP client at this document (RFC 9728 §5.1) ---

Scenario: POST /mcp without credentials - the 401 points at the metadata of the MCP server
    Given path '/mcp'
    And request {}
    When method POST
    Then status 401
    * def challenges = responseHeaders['WWW-Authenticate']
    And match challenges contains 'Bearer resource_metadata="http://localhost:8080/.well-known/oauth-protected-resource/mcp"'
    # the Basic challenge stays beside it: the client reads the scheme it understands
    * def basic = karate.filter(challenges, function(c){ return c.startsWith('Basic') })
    And match basic == '#[1]'

Scenario: POST /mcp with an API key that does not exist - the same challenge as a revoked or expired one
    Given path '/mcp'
    And header Authorization = 'Bearer rhak_this-key-was-never-issued'
    And request {}
    When method POST
    Then status 401
    And match responseHeaders['WWW-Authenticate'] contains 'Bearer resource_metadata="http://localhost:8080/.well-known/oauth-protected-resource/mcp"'

Scenario: POST /mcp behind a TLS-terminating proxy - the metadata is named with the forwarded scheme
    Given path '/mcp'
    And header X-Forwarded-Proto = 'https'
    And request {}
    When method POST
    Then status 401
    And match responseHeaders['WWW-Authenticate'] contains 'Bearer resource_metadata="https://localhost:8080/.well-known/oauth-protected-resource/mcp"'

Scenario: GET /.well-known/oauth-protected-resource/mcp behind a TLS-terminating proxy - the document agrees with the challenge
    Given path '/.well-known/oauth-protected-resource/mcp'
    And header X-Forwarded-Proto = 'https'
    When method GET
    Then status 200
    And match response.resource == 'https://localhost:8080/mcp'
    And match response.authorization_servers[0] == 'https://localhost:8080'

Scenario: a refused request outside the MCP server carries no Bearer challenge
    Given path '/secho'
    When method GET
    Then status 401
    * def bearer = karate.filter(responseHeaders['WWW-Authenticate'], function(c){ return c.startsWith('Bearer') })
    And match bearer == '#[0]'

Scenario: POST /mcp with No-Auth-Challenge - no challenge at all, Bearer included
    Given path '/mcp'
    And header No-Auth-Challenge = 'true'
    And request {}
    When method POST
    Then status 401
    And match responseHeaders['WWW-Authenticate'] == '#notpresent'
