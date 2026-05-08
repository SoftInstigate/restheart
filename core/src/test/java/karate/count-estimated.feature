Feature: count=estimated query param on _size and collection list

Background:
* url 'http://localhost:8080'
* def db = '/test-count-estimated'
* def coll = '/test-count-estimated/coll'
* def collSize = '/test-count-estimated/coll/_size'
* def authHeader = 'Basic YWRtaW46c2VjcmV0'

Scenario: Setup test data
    * header Authorization = authHeader
    Given path db
    And request { }
    When method PUT
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path coll
    And request { }
    When method PUT
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path coll
    And request [ {"_id": "1", "k": "a"}, {"_id": "2", "k": "a"}, {"_id": "3", "k": "b"} ]
    When method POST
    Then status 200

Scenario: GET _size without count param returns exact count and exact strategy header
    * header Authorization = authHeader
    Given path collSize
    When method GET
    Then status 200
    And match response._size == 3
    And match responseHeaders contains { X-Count-Strategy: ['exact'] }

Scenario: GET _size?count keeps exact strategy (count is redundant on _size)
    * header Authorization = authHeader
    Given path collSize
    And param count = ''
    When method GET
    Then status 200
    And match response._size == 3
    And match responseHeaders contains { X-Count-Strategy: ['exact'] }

Scenario: GET _size?count=true keeps exact strategy
    * header Authorization = authHeader
    Given path collSize
    And param count = 'true'
    When method GET
    Then status 200
    And match response._size == 3
    And match responseHeaders contains { X-Count-Strategy: ['exact'] }

Scenario: GET _size?count=estimated returns count via metadata, header reports estimated
    * header Authorization = authHeader
    Given path collSize
    And param count = 'estimated'
    When method GET
    Then status 200
    And match response._size == 3
    And match responseHeaders contains { X-Count-Strategy: ['estimated'] }

Scenario: GET _size?count=estimated with filter falls back to exact countDocuments
    * header Authorization = authHeader
    Given path collSize
    And param count = 'estimated'
    And param filter = '{"k": "a"}'
    When method GET
    Then status 200
    And match response._size == 2
    And match responseHeaders contains { X-Count-Strategy: ['exact'] }

Scenario: GET _size with filter (no estimate) returns exact filtered count
    * header Authorization = authHeader
    Given path collSize
    And param filter = '{"k": "a"}'
    When method GET
    Then status 200
    And match response._size == 2
    And match responseHeaders contains { X-Count-Strategy: ['exact'] }

Scenario: GET coll?count=estimated returns docs and estimated count
    * header Authorization = authHeader
    Given path coll
    And param count = 'estimated'
    When method GET
    Then status 200
    And match response._size == 3
    And match responseHeaders contains { X-Count-Strategy: ['estimated'] }

Scenario: Backward compat - count param without value still triggers exact count
    * header Authorization = authHeader
    Given path coll
    And param count = ''
    When method GET
    Then status 200
    And match response._size == 3
    And match responseHeaders contains { X-Count-Strategy: ['exact'] }

Scenario: count value other than estimated is treated as exact
    * header Authorization = authHeader
    Given path coll
    And param count = 'true'
    When method GET
    Then status 200
    And match response._size == 3
    And match responseHeaders contains { X-Count-Strategy: ['exact'] }

Scenario: Cleanup
    # fetch the db ETag, required by DELETE via If-Match
    * header Authorization = authHeader
    Given path db
    When method GET
    Then status 200
    * def etag = responseHeaders['ETag'][0]

    * headers { Authorization: '#(authHeader)', 'If-Match': '#(etag)' }
    Given path db
    When method DELETE
    Then status 204
