Feature: Test PATCH with aggregation update

Background:
* url 'http://localhost:8080'
* def db = '/test-patch-with-aggregation'
* def coll = '/test-patch-with-aggregation/coll'
* def doc = '/test-patch-with-aggregation/coll/doc'
* def authHeader = 'Basic YWRtaW46c2VjcmV0'

# CREATE TEST DATA

Scenario: Create test db and collection
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

Scenario: Create document with aggregation pipeline
    * header Authorization = authHeader
    Given path doc
    And param wm = "upsert"
    And request [ { "$set": { "foo": 1, "date": "$$NOW" } }, { "$set": { "bar": { "$switch": { "branches": [ { "case": { "$gte": [ "$foo", 0 ] }, "then": true } ], "default": false } } } }]
    When method PATCH
    Then assert responseStatus == 201

Scenario: Check created document
    * header Authorization = authHeader
    Given path doc
    When method GET
    Then assert responseStatus == 200
    And match response.foo == 1
    And match response.date == { "$date": '#? _ > 1677696175989' }
    And match response.bar == true