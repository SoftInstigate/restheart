Feature: test json schema

Background:
* url 'http://localhost:18080'
* def db = '/test-json-schema'
* def coll = db + '/coll'
# note: db starting with 'test-' are automatically deleted after test finishes

Scenario: create test db
    Given path db
    And request {}
    When method PUT
    Then status 201

Scenario: create test collection
    Given path coll
    And request {}
    When method PUT
    Then status 201

Scenario: simple GET request
    Given path coll
    When method GET
    Then status 200