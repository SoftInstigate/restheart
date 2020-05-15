Feature: test sessions

Background:
* url 'http://localhost:8080'
# note: db starting with 'test-' are automatically deleted after test finishes
* def db = '/test-sessions'
* def coll = '/coll'
* def sid = function(url) { return url.substring(url.length-36); }

@requires-mongodb-3.6
Scenario: create a session and use it for inserts and queries
    Given path db
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    Given path db + coll
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    Given path '/_sessions'
    And request {}
    When method POST
    Then status 201
    And match header Location contains '/_sessions/'
    * def sid = sid(responseHeaders['Location'][0])

    Given path '/_sessions/' + sid + '/_txns'
    When method GET
    Then status 200
    And match response == { currentTxn: null }

    Given path db + coll
    And request [ { 'a': 101 }, { 'a': 102 }, { 'a': 103 }, { 'a': 104 }, { 'a': 105 } ]
    And param sid = sid
    When method POST
    Then status 200

    Given path db + coll + "/_size"
    And param np = 'true'
    And param sid = sid
    And method GET
    Then status 200
    And match response._size == 5

@requires-mongodb-4 @requires-replica-set
Scenario: try to use invalid sid
    Given path db + coll
    And param sid = 'invalid'
    When method GET
    Then status 406