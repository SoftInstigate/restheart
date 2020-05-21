Feature: test txns

Background:
* def common = callonce read('common-once-txns.feature')
* url common.baseUrl
* def txn = call read('common-start-txn.feature') { baseUrl: '#(common.baseUrl)' }

@requires-mongodb-4 @requires-replica-set
Scenario: create a txn, document and commit
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

    * call read('common-commit-txn.feature') { baseUrl: '#(common.baseUrl)', sid: '#(txn.sid)', txn: 1 }

    Given path common.db + common.coll + '/' + docid
    And method GET
    Then status 200

@requires-mongodb-4 @requires-replica-set
Scenario: create a txn, a document, check isolation and abort txn
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

    * call read('common-abort-txn.feature') { baseUrl: '#(common.baseUrl)', sid: '#(txn.sid)', txn: 1 }

    Given path common.db + common.coll + '/' + docid
    And method GET
    Then status 404