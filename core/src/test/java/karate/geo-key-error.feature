# A document with invalid GeoJSON on a 2dsphere-indexed field is a client error (400), not a 500.
# MongoDB 9.0 returns error code 510 (GeoKeyExtractionFailed), earlier versions 16755.

@geo-key-error
Feature: Invalid GeoJSON on a 2dsphere index returns 400

  Background:
    * url baseUrl
    * def authHeader = adminAuth

  Scenario: setup
    * header Authorization = authHeader
    Given path '/test-geo-key-error'
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path '/test-geo-key-error/coll'
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path '/test-geo-key-error/coll/_indexes/loc2dsphere'
    And request { "keys": { "loc": "2dsphere" } }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

  Scenario: a point out of bounds is rejected with 400 and the reason from MongoDB
    * header Authorization = authHeader
    Given path '/test-geo-key-error/coll'
    And request { "loc": { "type": "Point", "coordinates": [ 500, 500 ] } }
    When method POST
    Then status 400
    And match response.message contains "extract geo keys"

  Scenario: a valid point is accepted
    * header Authorization = authHeader
    Given path '/test-geo-key-error/coll'
    And request { "loc": { "type": "Point", "coordinates": [ 9.19, 45.46 ] } }
    When method POST
    Then status 201
