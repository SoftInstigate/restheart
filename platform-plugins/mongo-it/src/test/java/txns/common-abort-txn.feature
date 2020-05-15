@ignore
Feature: feature that aborts a txn

# call as follows 
# call read('common-abort-txn.feature') { baseUrl: <baseUrl>, sid: <sessionId>, txn: txnNum }

Background:
* url baseUrl

@requires-mongodb-4 @requires-replica-set
Scenario: check session and abort txn
    # Given path '/_sessions/' + sid + '/_txns'
    # When method GET
    # Then status 200
    # And match response.currentTxn.status == 'IN'
    # And match response.currentTxn.id == txn

    Given path '/_sessions/' + sid + '/_txns/' + txn
    And request {}
    When method DELETE
    Then status 204

    Given path '/_sessions/' + sid + '/_txns'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'ABORTED'
    And match response.currentTxn.id == txn