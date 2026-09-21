# The Location of a created document names the URL a client can follow (#749). Behind a proxy that
# terminates TLS the exchange is plain http; the header must carry the scheme the proxy forwarded.

@location
Feature: Location header behind a TLS-terminating proxy (#749)

  Background:
    * url baseUrl
    * def authHeader = adminAuth

  Scenario: setup
    * header Authorization = authHeader
    Given path '/test-location-header'
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path '/test-location-header/coll'
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

  Scenario: without a proxy the Location is the request's own URL
    * header Authorization = authHeader
    Given path '/test-location-header/coll'
    And request { "a": 1 }
    When method POST
    Then status 201
    And match responseHeaders['Location'][0] == '#regex http://localhost:8080/test-location-header/coll/.+'

  Scenario: behind a TLS-terminating proxy the Location carries the forwarded scheme
    * header Authorization = authHeader
    * header X-Forwarded-Proto = 'https'
    Given path '/test-location-header/coll'
    And request { "a": 2 }
    When method POST
    Then status 201
    And match responseHeaders['Location'][0] == '#regex https://localhost:8080/test-location-header/coll/.+'

  Scenario: a forwarded host is honoured too
    * header Authorization = authHeader
    * header X-Forwarded-Proto = 'https'
    * header X-Forwarded-Host = 'api.example.com'
    Given path '/test-location-header/coll'
    And request { "a": 3 }
    When method POST
    Then status 201
    And match responseHeaders['Location'][0] == '#regex https://api.example.com/test-location-header/coll/.+'
