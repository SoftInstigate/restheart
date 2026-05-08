Feature: caching query parameter behavior on collection GET

Background:
* url 'http://localhost:8080'
* def db = '/test-caching'
* def coll = '/test-caching/coll'
* def authHeader = 'Basic YWRtaW46c2VjcmV0'
* def sidFromLocation = function(location) { return location.substring(location.length-36); }

Scenario: Setup test data
    * header Authorization = authHeader
    Given path db
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path coll
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path coll
        And request
        """
        [
            { "_id": "1", "k": "a", "v": 1 },
            { "_id": "2", "k": "a", "v": 2 },
            { "_id": "3", "k": "b", "v": 3 },
            { "_id": "4", "k": "b", "v": 4 }
        ]
        """
    When method POST
    Then status 200

Scenario: GET coll with cache returns paginated data without count metadata unless requested
    * header Authorization = authHeader
    Given path coll
    And param cache = ''
    And param page = 1
    And param pagesize = 2
    And param sort_by = '_id'
    When method GET
    Then status 200
    And match response._returned == 2
    And match response._size == '#notpresent'

Scenario: GET coll with cache and count computes exact count and reports strategy
    * header Authorization = authHeader
    Given path coll
    And param cache = ''
    And param count = ''
    And param page = 1
    And param pagesize = 2
    And param sort_by = '_id'
    When method GET
    Then status 200
    And match response._returned == 2
    And match response._size == 4
    And match responseHeaders['X-Count-Strategy'][0] == 'exact'

Scenario: GET coll with cache and count=estimated reports estimated strategy
    * header Authorization = authHeader
    Given path coll
    And param cache = ''
    And param count = 'estimated'
    And param page = 1
    And param pagesize = 2
    And param sort_by = '_id'
    When method GET
    Then status 200
    And match response._returned == 2
    And match response._size == 4
    And match responseHeaders['X-Count-Strategy'][0] == 'estimated'

Scenario: GET coll with cache count=estimated and filter falls back to exact strategy
    * header Authorization = authHeader
    Given path coll
    And param cache = ''
    And param count = 'estimated'
    And param filter = '{"k":"a"}'
    And param page = 1
    And param pagesize = 2
    And param sort_by = '_id'
    When method GET
    Then status 200
    And match response._returned == 2
    And match response._size == 2
    And match responseHeaders['X-Count-Strategy'][0] == 'exact'

Scenario: Cleanup
    * header Authorization = authHeader
    Given path db
    When method GET
    Then status 200
    * def etag = responseHeaders['ETag'][0]

    * headers { Authorization: '#(authHeader)', 'If-Match': '#(etag)' }
    Given path db
    When method DELETE
    Then status 204