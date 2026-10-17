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

    * header Authorization = authHeader
    Given path '/test-location-header/files.files'
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

  Scenario: the Location of an uploaded file carries the forwarded scheme and host too
    * header Authorization = authHeader
    * header X-Forwarded-Proto = 'https'
    * header X-Forwarded-Host = 'api.example.com'
    Given path '/test-location-header/files.files'
    And multipart file file = { read: 'RESTHeart.pdf', filename: 'location.pdf' }
    And multipart field metadata = '{"filename": "location.pdf"}'
    When method POST
    Then status 201
    And match responseHeaders['Location'][0] == '#regex https://api.example.com/test-location-header/files.files/.+'

  Scenario: the Location of a new session carries the forwarded scheme and host too
    * header Authorization = authHeader
    * header X-Forwarded-Proto = 'https'
    * header X-Forwarded-Host = 'api.example.com'
    Given path '/_sessions'
    And request { }
    When method POST
    Then status 201
    And match responseHeaders['Location'][0] == '#regex https://api.example.com/_sessions/.+'

  Scenario: an instance-base-url attached for the tenant wins over the forwarded headers
    # a multi-tenant deployment attaches override-mongo-instance-base-url per request; here the
    # test-plugins' instanceBaseUrlOverrideInterceptor does, from a query parameter
    * header Authorization = authHeader
    * header X-Forwarded-Proto = 'https'
    * header X-Forwarded-Host = 'api.example.com'
    Given path '/test-location-header/coll'
    And param _instance-base-url-override = 'https://tenant1.example.com'
    And request { "a": 4 }
    When method POST
    Then status 201
    And match responseHeaders['Location'][0] == '#regex https://tenant1.example.com/test-location-header/coll/.+'
