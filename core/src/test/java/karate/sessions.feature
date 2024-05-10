Feature: test sessions

Background:
* url 'http://localhost:8080'
# note: db starting with 'test-' are automatically deleted after test finishes
* def db = '/test-sessions'
* def coll = '/coll'
* def sidFromLocation = function(location) { return location.substring(location.length-36); }
* def authHeader = 'Basic YWRtaW46c2VjcmV0'

@requires-mongodb-3.6
Scenario: create a session and use it for inserts and queries
    * header Authorization = authHeader
    Given path db
    And param rep = 's'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path db + coll
    And param rep = 's'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path '/_sessions'
    And param rep = 's'
    And request {}
    When method POST
    Then status 201
    And match header Location contains '/_sessions/'
    * def sid = sidFromLocation(responseHeaders['Location'][0])

    * header Authorization = authHeader
    Given path '/_sessions/' + sid + '/_txns'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == { currentTxn: null }

    * header Authorization = authHeader
    Given path db + coll
    And request [ { 'a': 101 }, { 'a': 102 }, { 'a': 103 }, { 'a': 104 }, { 'a': 105 } ]
    And param sid = sid
    And param rep = 's'
    When method POST
    Then status 200

    * header Authorization = authHeader
    Given path db + coll + "/_size"
    And param np = 'true'
    And param sid = sid
    And param rep = 's'
    And method GET
    Then status 200
    And match response._size == 5

@requires-mongodb-4 @requires-replica-set
Scenario: try to use invalid sid
    * header Authorization = authHeader
    Given path db + coll
    And param sid = 'invalid'
    And param rep = 's'
    When method GET
    Then status 400