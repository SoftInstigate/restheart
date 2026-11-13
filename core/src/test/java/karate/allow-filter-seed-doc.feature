@ignore
Feature: one document with status A in a collection of test-allow-filter

Scenario:
    * url baseUrl
    * header Authorization = adminAuth
    Given path '/test-allow-filter/' + __arg.coll + '/doc1'
    And param wm = 'upsert'
    And request { "status": "A" }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200
