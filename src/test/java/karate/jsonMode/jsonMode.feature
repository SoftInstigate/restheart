Feature: Test jsonMode query parameter

Background:
* url 'http://localhost:18080'
* def db = '/test-jsonMode'
* def coll = '/test-jsonMode/coll'
* def doc = '/test-jsonMode/coll/doc'

# CREATE TEST DATA

Scenario: Create test data
    Given path db
    And request { }
    When method PUT
    Then assert responseStatus == 201

    Given path coll
    And request { }
    When method PUT
    Then assert responseStatus == 201

    Given path doc
    And request { "int": 1, "double": 1.0, "long": 1000000000, "timestamp": { "$date": 1568295769260 } }
    When method PUT
    Then assert responseStatus == 201

Scenario: Get document without jsonMode qparam
    Given path doc
    When method GET
    Then assert responseStatus == 200
    And match response.int == 1
    And match response.double == 1.0
    And match response.long == 1000000000
    And match response.timestamp.$date == 1568295769260

Scenario: Get document with jsonMode=strict
    Given path doc
    And param jsonMode = "strict"
    When method GET
    Then assert responseStatus == 200
    And match response.int == 1
    And match response.double == 1.0
    And match response.long == 1000000000
    And match response.timestamp.$date == 1568295769260

Scenario: Get document with jsonMode=extended
    Given path doc
    And param jsonMode = "extended"
    When method GET
    Then assert responseStatus == 200
    And match response.int.$numberInt == "1"
    And match response.double.$numberDouble == "1.0"
    And match response.long.$numberInt == "1000000000"
    And match response.timestamp.$date.$numberLong == "1568295769260"

Scenario: Get document with jsonMode=relaxed
    Given path doc
    And param jsonMode = "relaxed"
    When method GET
    Then assert responseStatus == 200
    And match response.int. == 1
    And match response.double == 1.0
    And match response.long == 1000000000
    And match response.timestamp.$date == "2019-09-12T13:42:49.26Z"
