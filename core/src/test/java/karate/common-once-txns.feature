@ignore
Feature: init feature for txn, creats test db and collection + define sono useful functions

Background:
* def baseUrl = 'http://localhost:8080'
# note: db starting with 'test-' are automatically deleted after test finishes
* def db = '/test-txns'
* def coll = '/coll'
* def sid = function(url) { return url.substring(url.length-36); }
* def docid = function(url) { return url.substring(url.length-24); }
* def authHeader = 'Basic YWRtaW46c2VjcmV0'

Scenario:
    * header Authorization = authHeader
    Given url baseUrl
    And param rep = 's'
    And path db
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path db + coll
    And param rep = 's'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200