@ignore
Feature: feature that aborts a txn

# call as follows 
# call read('common-commit-txn.feature') { baseUrl: <baseUrl>, sid: <sessionId>, txn: txnNum }

Background:
* url baseUrl
* def authHeader = 'Basic YWRtaW46c2VjcmV0'

@requires-mongodb-4 @requires-replica-set
Scenario: check session and abort txn
    # Given path '/_sessions/' + sid + '/_txns'
    # When method GET
    # Then status 200
    # And match response.currentTxn.status == 'IN'
    # And match response.currentTxn.id == txn

    * header Authorization = authHeader
    Given path '/_sessions/' + sid + '/_txns/' + txn
    And param rep = 's'
    And request {}
    When method PATCH
    Then status 200

    * header Authorization = authHeader
    Given path '/_sessions/' + sid + '/_txns'
    And param rep = 's'
    When method GET
    Then status 200
    And match response.currentTxn.status == 'COMMITTED'
    And match response.currentTxn.id == txn