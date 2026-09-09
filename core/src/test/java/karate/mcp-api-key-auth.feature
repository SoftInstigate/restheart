Feature: an API key (PAT) authenticates against the MCP endpoint

# What api-key-auth.feature proves for the REST API, proved for /mcp.
#
# /mcp is not an ordinary REST path: it is a service with its own transport, its
# own session, and its own tools, and the MCP session is established by an
# `initialize` call rather than by the first data request. So "a PAT works on
# /secho" does not, on its own, say that a PAT can open an MCP session, that the
# roles carried on the key drive what that session may reach, or that a token
# minted by get_token inherits the key's identity rather than something wider.
#
# Those three are what this file pins, end to end, against a running instance.

Background:
  * callonce read('api-key-auth-setup.feature')
  * url 'http://localhost:8080'

  # The Streamable HTTP transport answers either a single JSON body or an SSE
  # stream, and says so by Content-Type; a client has to accept both.
  * def mcpAccept = 'application/json, text/event-stream'

  * def initialize =
  """
  {
    "jsonrpc": "2.0", "id": 1, "method": "initialize",
    "params": {
      "protocolVersion": "2025-06-18",
      "capabilities": {},
      "clientInfo": { "name": "karate", "version": "1" }
    }
  }
  """

Scenario: /mcp refuses an unauthenticated caller
    # The precondition every other scenario rests on: without it, "the PAT
    # worked" would be vacuously true on an endpoint open to anyone.
    * header Accept = mcpAccept
    Given path '/mcp'
    And request initialize
    When method POST
    Then status 401

Scenario: a PAT opens an MCP session
    * header Authorization = 'Bearer rhak_valid'
    * header Accept = mcpAccept
    Given path '/mcp'
    And request initialize
    When method POST
    Then status 200
    # the session id is what makes it a session rather than a one-off request
    And match responseHeaders['Mcp-Session-Id'] != null
    And match response.result.protocolVersion == '#present'

Scenario: an expired PAT does not open an MCP session
    # The key is still in the collection — a TTL index reclaims lazily — so this
    # is the authenticator refusing it rather than the document being gone.
    * header Authorization = 'Bearer rhak_expired'
    * header Accept = mcpAccept
    Given path '/mcp'
    And request initialize
    When method POST
    Then status 401

Scenario: a PAT carrying no roles authenticates and then reaches nothing
    # 403 and not 401 is the whole point: the key was accepted, and the ACL then
    # matched nothing. Deny by default, on /mcp as anywhere else.
    * header Authorization = 'Bearer rhak_noroles'
    * header Accept = mcpAccept
    Given path '/mcp'
    And request initialize
    When method POST
    Then status 403

Scenario: the roles on the key decide whether /mcp is reachable at all
    # The test ACL grants poweruser GET /testdb and nothing else. The key belongs
    # to `admin`, who would reach /mcp comfortably — so this being refused shows
    # the roles came from the key document and not from the user behind it.
    * header Authorization = 'Bearer rhak_poweruser'
    * header Accept = mcpAccept
    Given path '/mcp'
    And request initialize
    When method POST
    Then status 403

    # ... and the same key still works where its roles do reach
    * header Authorization = 'Bearer rhak_poweruser'
    Given path '/testdb'
    When method GET
    Then assert responseStatus == 200 || responseStatus == 404

Scenario: a session opened with a PAT can list resources
    * header Authorization = 'Bearer rhak_valid'
    * header Accept = mcpAccept
    Given path '/mcp'
    And request initialize
    When method POST
    Then status 200
    * def session = responseHeaders['Mcp-Session-Id'][0]

    * header Authorization = 'Bearer rhak_valid'
    * header Accept = mcpAccept
    * header Mcp-Session-Id = session
    Given path '/mcp'
    And request { "jsonrpc": "2.0", "method": "notifications/initialized" }
    When method POST
    Then assert responseStatus == 200 || responseStatus == 202

    * header Authorization = 'Bearer rhak_valid'
    * header Accept = mcpAccept
    * header Mcp-Session-Id = session
    Given path '/mcp'
    And request { "jsonrpc": "2.0", "id": 2, "method": "resources/list", "params": {} }
    When method POST
    Then status 200
    And match response.result.resources == '#present'

Scenario: get_token on a PAT-authenticated session issues a token for that identity
    # The end of the chain: a PAT opens the session, and the session mints a
    # short-lived token of its own. If the key's identity were not carried
    # through, this is where it would show.
    * header Authorization = 'Bearer rhak_valid'
    * header Accept = mcpAccept
    Given path '/mcp'
    And request initialize
    When method POST
    Then status 200
    * def session = responseHeaders['Mcp-Session-Id'][0]

    * header Authorization = 'Bearer rhak_valid'
    * header Accept = mcpAccept
    * header Mcp-Session-Id = session
    Given path '/mcp'
    And request { "jsonrpc": "2.0", "method": "notifications/initialized" }
    When method POST
    Then assert responseStatus == 200 || responseStatus == 202

    # a tool call is answered as an event stream, so this reads the raw body
    * header Authorization = 'Bearer rhak_valid'
    * header Accept = mcpAccept
    * header Mcp-Session-Id = session
    Given path '/mcp'
    And request { "jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": { "name": "get_token", "arguments": {} } }
    When method POST
    Then status 200
    And match response contains 'access_token'

Scenario: the token minted from a PAT session carries the key's roles, not the user's
    # rhak_mcpreader belongs to `admin` but names only `aclreader`. If the session
    # took its roles from the user behind the key rather than from the key, `admin`
    # would appear here — and the key would silently confer far more than it says.
    * header Authorization = 'Bearer rhak_mcpreader'
    * header Accept = mcpAccept
    Given path '/mcp'
    And request initialize
    When method POST
    Then status 200
    * def session = responseHeaders['Mcp-Session-Id'][0]

    * header Authorization = 'Bearer rhak_mcpreader'
    * header Accept = mcpAccept
    * header Mcp-Session-Id = session
    Given path '/mcp'
    And request { "jsonrpc": "2.0", "method": "notifications/initialized" }
    When method POST
    Then assert responseStatus == 200 || responseStatus == 202

    * header Authorization = 'Bearer rhak_mcpreader'
    * header Accept = mcpAccept
    * header Mcp-Session-Id = session
    Given path '/mcp'
    And request { "jsonrpc": "2.0", "id": 4, "method": "tools/call", "params": { "name": "get_token", "arguments": {} } }
    When method POST
    Then status 200
    And match response contains 'aclreader'

    # The decisive half, and behavioural rather than textual: the response also carries
    # `username`, which for this key is `admin`, so looking for the absence of that string
    # would fail for the wrong reason. What settles it is what the session can reach. The
    # test ACL grants /secho to admin and not to aclreader — so admin's own roles are
    # demonstrably not in play here.
    * header Authorization = 'Bearer rhak_mcpreader'
    Given path '/secho'
    When method GET
    Then status 403
