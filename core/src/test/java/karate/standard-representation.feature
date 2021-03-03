Feature: test standard representation format

Background:
* url 'http://localhost:8080'
* def root = '/'
* def db = '/test-std-rep'
* def coll = '/test-std-rep/coll'
* def doc1 = '/test-std-rep/coll/doc1'
* def doc2 = '/test-std-rep/coll/doc2'
* def authHeader = 'Basic YWRtaW46c2VjcmV0'
* def isArray =
"""
function(resp) {
  return resp && JSON.parse(resp).isArray();
}
"""

Scenario: Create test data
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
    Given path doc1
    And param wm = "upsert"
    And request { "int": 1 }
    When method PUT
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path doc2
    And param wm = "upsert"
    And request { "int": 2 }
    When method PUT
    Then assert responseStatus == 201

Scenario: Get root
    * header Authorization = authHeader
    Given path root
    And param rep = "s"
    When method GET
    Then status 200
    And match responseType == 'json'
    # should be an array of strings
    And match response == '#[] #string'
    # containing 'test-std-rep'
    And match response contains "test-std-rep"

Scenario: Get root page=100
    * header Authorization = authHeader
    Given path root
    And param rep = "s"
    And param page = "100"
    When method GET
    Then status 200
    And match responseType == 'json'
    # should be an empty array of strings
    And match response == '#[0]'

Scenario: Get db
    * header Authorization = authHeader
    Given path db
    And param rep = "s"
    When method GET
    Then status 200
    And match responseType == 'json'
    # should be an array of strings
    And match response == '#[] #string'
    # containing 'coll'
    And match response contains "coll"

Scenario: Get db page=100
    * header Authorization = authHeader
    Given path db
    And param rep = "s"
    And param page = "100"
    When method GET
    Then status 200
    And match responseType == 'json'
    # should be an empty array of strings
    And match response == '#[0]'

Scenario: Get coll default sort (-_id)
    * header Authorization = authHeader
    Given path coll
    And param rep = "s"
    When method GET
    Then status 200
    And match responseType == 'json'
    # should be an array of size 2
    And match response == '#[2]'
    # should contain doc1 and doc2
    And match response[0] ==
"""
{
    _id: 'doc2',
    int: 2,
    _etag: { $oid: #string },
}
"""
    And match response[1] ==
"""
{
    _id: 'doc1',
    int: 1,
    _etag: { $oid: #string },
}
"""

Scenario: Get coll sort=+_id
    * header Authorization = authHeader
    Given path coll
    And param rep = "s"
    And param sort = '{"_id": 1}'
    When method GET
    Then status 200
    And match responseType == 'json'
    # should be an array of size 2
    And match response == '#[2]'
    # should contain doc1 and doc2
    And match response[0] ==
"""
{
    _id: 'doc1',
    int: 1,
    _etag: { $oid: #string },
}
"""
    And match response[1] ==
"""
{
    _id: 'doc2',
    int: 2,
    _etag: { $oid: #string },
}
"""

Scenario: Get coll with pagesize=1 and page=1
    * header Authorization = authHeader
    Given path coll
    And param rep = "s"
    And param page = "1"
    And param pagesize = "1"
    And param sort = '{"_id": 1}'
    When method GET
    Then status 200
    And match responseType == 'json'
    # should contain doc1
    And match response == '#[1]'
    # should containing one objects with _id, int and _etag properties
    And match response[0] ==
"""
{
    _id: 'doc1',
    int: 1,
    _etag: { $oid: #string },
}
"""

Scenario: Get coll with pagesize=1 and page=2
    * header Authorization = authHeader
    Given path coll
    And param rep = "s"
    And param page = "2"
    And param pagesize = "1"
    And param sort = '{"_id": 1}'
    When method GET
    Then status 200
    And match responseType == 'json'
    # should contain doc2
    And match response == '#[1]'
    # should containing one objects with _id, int and _etag properties
    And match response[0] ==
"""
{
    _id: 'doc2',
    int: 2,
    _etag: { $oid: #string },
}
"""

Scenario: Get coll with pagesize=1 and page=3
    * header Authorization = authHeader
    Given path coll
    And param rep = "s"
    And param page = "3"
    And param pagesize = "1"
    And param sort = '{"_id": 1}'
    When method GET
    Then status 200
    And match responseType == 'json'
    # should be an empty array
    And match response == '#[0]'

Scenario: Get doc
    * header Authorization = authHeader
    Given path doc1
    And param rep = "s"
    When method GET
    Then status 200
    And match responseType == 'json'
    # should be an object with _id, int and _etag properties
    And match response ==
"""
{
    _id: 'doc1',
    int: 1,
    _etag: { $oid: #string },
}
"""

Scenario: Bulk POST coll
    * header Authorization = authHeader
    Given path coll
    And param rep = "s"
    And request [ { "int": 3},  { "int": 4 } ]
    When method POST
    Then status 200
    And match responseType == 'json'
    # should be an empty array
    And match response ==
"""
{
    "inserted": 2,
    "links": #[] #string,
    "deleted": 0,
    "modified": 0,
    "matched": 0
}
"""

Scenario: Errored request
    * header Authorization = authHeader
    Given path coll
    And param rep = "s"
    And param pagesize = "a"
    When method GET
    Then status 400
    And match responseType == 'json'
    # should be an empty array
    And match response ==
"""
{
    "http status code": 400,
    "http status description": "Bad Request",
    "message": #string,
    "exception":"java.lang.NumberFormatException",
    "exception message": "For input string: 'a'"
}
"""
