Feature: test json schema

Background:
* url 'http://localhost:18080'
# note: db starting with 'test-' are automatically deleted after test finishes
* def db = '/test-txns'
* def coll = '/coll'
* def collUrl = 'http://localhost:18080/test-txns/coll'
* def sid = function(url) { return url.substring(url.length-36); }
* def docid = function(url) { return url.substring(url.length-24); }

@requires-mongodb-4 @requires-replica-set
Scenario: create a session, txn, document and commit
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

    Given path '/_sessions/' + sid + '/_txns'
    And request {}
    When method POST
    Then status 201

    Given path '/_sessions/' + sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'IN'
    And match response.currentTxn.id == 1

    Given path db + coll
    And request { 'a': 101 }
    And param sid = sid
    And param txn = 1
    When method POST
    Then status 201

    Given path db + coll
    And request { 'a': 101 }
    And param sid = sid
    And param txn = 1
    When method POST
    Then status 201
    And match header Location contains db
    * def docid = docid(responseHeaders['Location'][0])

    Given path db + coll + '/' + docid
    And method GET
    Then status 404

    Given path '/_sessions/' + sid + '/_txns/1'
    And request {}
    When method PATCH
    Then status 200

    Given path '/_sessions/' + sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'COMMITTED'
    And match response.currentTxn.id == 1

    Given path db + coll + '/' + docid
    And method GET
    Then status 200

@requires-mongodb-4 @requires-replica-set
Scenario: create a session, txn, document, check isolation and abort txn
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

    Given path '/_sessions/' + sid + '/_txns'
    And request {}
    When method POST
    Then status 201

    Given path '/_sessions/' + sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'IN'
    And match response.currentTxn.id == 1

    Given path db + coll
    And request { 'a': 101 }
    And param sid = sid
    And param txn = 1
    When method POST
    Then status 201

    Given path db + coll
    And request { 'a': 101 }
    And param sid = sid
    And param txn = 1
    When method POST
    Then status 201
    And match header Location contains db
    * def docid = docid(responseHeaders['Location'][0])

    Given path db + coll + '/' + docid
    And method GET
    Then status 404

    Given path '/_sessions/' + sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'IN'
    And match response.currentTxn.id == 1

    Given path db + coll + '/' + docid
    And param sid = sid
    And param txn = 1
    And method GET
    Then status 200

    Given path '/_sessions/' + sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'IN'
    And match response.currentTxn.id == 1

    Given path '/_sessions/' + sid + '/_txns/1'
    And request {}
    When method DELETE
    Then status 204

    Given path '/_sessions/' + sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'ABORTED'
    And match response.currentTxn.id == 1

    Given path db + coll + '/' + docid
    And method GET
    Then status 404