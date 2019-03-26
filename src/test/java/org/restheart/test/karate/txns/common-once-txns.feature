@ignore
Feature: init feature for txn

Background:
* def baseUrl = 'http://localhost:18080'
# note: db starting with 'test-' are automatically deleted after test finishes
* def db = '/test-txns'
* def coll = '/coll'
* def sid = function(url) { return url.substring(url.length-36); }
* def docid = function(url) { return url.substring(url.length-24); }

Scenario:
    Given url baseUrl
    And path db
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    Given path db + coll
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200