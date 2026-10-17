# A duplicate key error names the index and the key MongoDB reports. The fixed message listed three
# possible causes, and a client that lost a race to write the same _id could not tell which one it had hit.

@duplicate-key
Feature: Duplicate key error names the index and the key

  Background:
    * url baseUrl
    * def authHeader = adminAuth

  Scenario: setup
    * header Authorization = authHeader
    Given path '/test-duplicate-key'
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path '/test-duplicate-key/coll'
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path '/test-duplicate-key/coll/_indexes/uniqueEmail'
    And request { "keys": { "email": 1 }, "ops": { "unique": true } }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

  Scenario: an insert with an existing _id names the _id index and the key
    * header Authorization = authHeader
    Given path '/test-duplicate-key/coll'
    And param wm = 'insert'
    And request { "_id": "accept:o1" }
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path '/test-duplicate-key/coll'
    And param wm = 'insert'
    And request { "_id": "accept:o1" }
    When method POST
    Then status 409
    And match response.message contains "Duplicate key: index _id_, key { _id: 'accept:o1' }"

  Scenario: a unique index violation names that index and the key
    * header Authorization = authHeader
    Given path '/test-duplicate-key/coll'
    And request { "email": "a@b.c" }
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path '/test-duplicate-key/coll'
    And request { "email": "a@b.c" }
    When method POST
    Then status 409
    And match response.message contains "Duplicate key: index uniqueEmail, key { email: 'a@b.c' }"
