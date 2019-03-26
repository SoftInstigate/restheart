Feature: test json schema

Background:
* def common = callonce read('common-once-txns.feature')
* url common.baseUrl
* def txn = call read('common-start-txn.feature') { baseUrl: '#(common.baseUrl)' }

@requires-mongodb-4 @requires-replica-set
Scenario: create a session, txn, document and commit
    Given path common.db + common.coll
    And request { 'a': 101 }
    And param sid = txn.sid
    And param txn = 1
    When method POST
    Then status 201

    Given path common.db + common.coll
    And request { 'a': 101 }
    And param sid = txn.sid
    And param txn = 1
    When method POST
    Then status 201
    And match header Location contains common.db
    * def docid = common.docid(responseHeaders['Location'][0])

    Given path common.db + common.coll + '/' + docid
    And method GET
    Then status 404

    Given path '/_sessions/' + txn.sid + '/_txns/1'
    And request {}
    When method PATCH
    Then status 200

    Given path '/_sessions/' + txn.sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'COMMITTED'
    And match response.currentTxn.id == 1

    Given path common.db + common.coll + '/' + docid
    And method GET
    Then status 200

@requires-mongodb-4 @requires-replica-set
Scenario: create a session, txn, document, check isolation and abort txn
    Given path common.db + common.coll
    And request { 'a': 101 }
    And param sid = txn.sid
    And param txn = 1
    When method POST
    Then status 201

    Given path common.db + common.coll
    And request { 'a': 101 }
    And param sid = txn.sid
    And param txn = 1
    When method POST
    Then status 201
    And match header Location contains common.db
    * def docid = common.docid(responseHeaders['Location'][0])

    Given path common.db + common.coll + '/' + docid
    And method GET
    Then status 404

    Given path '/_sessions/' + txn.sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'IN'
    And match response.currentTxn.id == 1

    Given path common.db + common.coll + '/' + docid
    And param sid = txn.sid
    And param txn = 1
    And method GET
    Then status 200

    Given path '/_sessions/' + txn.sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'IN'
    And match response.currentTxn.id == 1

    Given path '/_sessions/' + txn.sid + '/_txns/1'
    And request {}
    When method DELETE
    Then status 204

    Given path '/_sessions/' + txn.sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'ABORTED'
    And match response.currentTxn.id == 1

    Given path common.db + common.coll + '/' + docid
    And method GET
    Then status 404

@requires-mongodb-4 @requires-replica-set
Scenario: create a document in txn T1, create conflicting document in T2. T2 aborts, T1 can commit
    * def txn2 = call read('common-start-txn.feature') { baseUrl: '#(common.baseUrl)' }
    
    Given path '/_sessions/' + txn.sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'IN'
    And match response.currentTxn.id == 1

    Given path '/_sessions/' + txn2.sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'IN'
    And match response.currentTxn.id == 1

    Given path common.db + common.coll
    And request { _id: 1, a: 101 }
    And param sid = txn.sid
    And param txn = 1
    When method POST
    Then status 201

    Given path common.db + common.coll
    And request { _id: 1, a: 101 }
    And param sid = txn2.sid
    And param txn = 1
    When method POST
    Then status 500
    And print "**** TODO this 500 must be handled to return a better status code"

    Given path '/_sessions/' + txn.sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'IN'
    And match response.currentTxn.id == 1

    Given path '/_sessions/' + txn2.sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'ABORTED'
    And match response.currentTxn.id == 1

    Given path '/_sessions/' + txn.sid + '/_txns/1'
    And request {}
    When method PATCH
    Then status 200

    Given path '/_sessions/' + txn.sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'COMMITTED'
    And match response.currentTxn.id == 1