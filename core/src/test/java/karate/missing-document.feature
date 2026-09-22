# A write that does not create answers 404 for a missing document, and says which one, as a GET does.

@missing-document
Feature: 404 of a write to a missing document names the document

  Background:
    * url baseUrl
    * def authHeader = adminAuth
    * def db = '/test-missing-document'
    * def coll = db + '/coll'

  Scenario: setup
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

  Scenario: PUT, PATCH and DELETE of a missing document answer 404 with a message
    * header Authorization = authHeader
    Given path coll, 'ghost'
    And request { "a": 1 }
    When method PUT
    Then status 404
    And match response.message == "document 'ghost' does not exist"

    * header Authorization = authHeader
    Given path coll, 'ghost'
    And request { "a": 1 }
    When method PATCH
    Then status 404
    And match response.message == "document 'ghost' does not exist"

    * header Authorization = authHeader
    Given path coll, 'ghost'
    When method DELETE
    Then status 404
    And match response.message == "document 'ghost' does not exist"
