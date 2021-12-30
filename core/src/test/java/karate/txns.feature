Feature: test txns

Background:
* def common = callonce read('common-once-txns.feature')
* url common.baseUrl
* def txn = call read('common-start-txn.feature') { baseUrl: '#(common.baseUrl)' }
* def authHeader = 'Basic YWRtaW46c2VjcmV0'
* def docid = function(url) { return url.substring(url.length-24); }

@requires-mongodb-4 @requires-replica-set
Scenario: create a txn, document and commit
    * header Authorization = authHeader
    Given path common.db + common.coll
    And param rep = 's'
    And request { 'a': 101 }
    And param sid = txn.sid
    And param txn = 1
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path common.db + common.coll
    And param rep = 's'
    And request { 'a': 101 }
    And param sid = txn.sid
    And param txn = 1
    When method POST
    Then status 201
    And match header Location contains common.db
    * def docid = docid(responseHeaders['Location'][0])

    * header Authorization = authHeader
    Given path common.db + common.coll + '/' + docid
    And param rep = 's'
    And method GET
    Then status 404

    * call read('common-commit-txn.feature') { baseUrl: '#(common.baseUrl)', sid: '#(txn.sid)', txn: 1 }
    * header Authorization = authHeader
    Given path common.db + common.coll + '/' + docid
    And param rep = 's'
    And method GET
    Then status 200

@requires-mongodb-4 @requires-replica-set
Scenario: create a txn, a document, check isolation and abort txn
    * header Authorization = authHeader
    Given path common.db + common.coll
    And param rep = 's'
    And request { 'a': 101 }
    And param sid = txn.sid
    And param txn = 1
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path common.db + common.coll
    And param rep = 's'
    And request { 'a': 101 }
    And param sid = txn.sid
    And param txn = 1
    When method POST
    Then status 201
    And match header Location contains common.db
    * def docid = docid(responseHeaders['Location'][0])

    * header Authorization = authHeader
    Given path common.db + common.coll + '/' + docid
    And param rep = 's'
    And method GET
    Then status 404

    * header Authorization = authHeader
    Given path '/_sessions/' + txn.sid + '/_txns'
    And param rep = 's'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'IN'
    And match response.currentTxn.id == 1

    * header Authorization = authHeader
    Given path common.db + common.coll + '/' + docid
    And param rep = 's'
    And param sid = txn.sid
    And param txn = 1
    And method GET
    Then status 200

    * header Authorization = authHeader
    Given path '/_sessions/' + txn.sid + '/_txns'
    And param rep = 's'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'IN'
    And match response.currentTxn.id == 1

    * call read('common-abort-txn.feature') { baseUrl: '#(common.baseUrl)', sid: '#(txn.sid)', txn: 1 }

    * header Authorization = authHeader
    Given path common.db + common.coll + '/' + docid
    And param rep = 's'
    And method GET
    Then status 404