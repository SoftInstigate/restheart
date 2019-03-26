@ignore
Feature: feature that creates a session with in-progress txn

Background:
* url baseUrl

@requires-mongodb-4 @requires-replica-set
Scenario: create a session and start a txn
    Given path '/_sessions'
    And request {}
    When method POST
    Then status 201
    And match header Location contains '/_sessions/'
    * def sid = common.sid(responseHeaders['Location'][0])

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