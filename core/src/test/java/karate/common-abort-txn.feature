@ignore
Feature: feature that aborts a txn

# call as follows
# call read('common-abort-txn.feature') { baseUrl: <baseUrl>, sid: <sessionId>, txn: txnNum }

Background:
* url baseUrl
* def authHeader = 'Basic YWRtaW46c2VjcmV0'

@requires-mongodb-4 @requires-replica-set
Scenario: check session and abort txn
    * header Authorization = authHeader
    Given path '/_sessions/' + sid + '/_txns/' + txn
    And param rep = 's'
    And request {}
    When method DELETE
    Then status 204

    * header Authorization = authHeader
    Given path '/_sessions/' + sid + '/_txns'
    And param rep = 's'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'ABORTED'
    And match response.currentTxn.id == txn