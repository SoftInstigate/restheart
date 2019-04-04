Feature: Test Change Streams

Background:
* url 'http://localhost:18080'
* def db = '/test-change-streams'
* def coll = db + '/coll'
* callonce read('./features/set_up_test_environment.feature')

# SUCCESSFULL SCENARIOS SECTION

@requires-mongodb-3.6 @requires-replica-set
Scenario: Delete a Change Stream resource.
    Given path coll + '/_streams/changeStreamOperation/toBeDeletedStream'
    When method delete
    Then status 204

@requires-mongodb-3.6 @requires-replica-set
Scenario: GET the Change Stream resources associated to given changeStreamOperation
    Given path coll + '/_streams/changeStreamOperation'
    When method get
    Then status 200

@requires-mongodb-3.6 @requires-replica-set
Scenario: Open Change Stream resources and listen for "globalNotificationsStream" ws message after POSTing a document.
    
    # Establish WebSocket connection to get notified.
    Given def streamPath = '/_streams/changeStreamOperation/globalNotificationsStream'
    And def baseUrl = 'http://localhost:18080'
    And def handler = function(notification) { java.lang.Thread.sleep(1000); karate.signal(notification) }
    And def host = baseUrl + coll + streamPath    
    Then def socket = karate.webSocket(host, handler)

    Given path coll
    And request {}

    When method POST
    And def result = karate.listen(5000)
    Then match result == '#notnull'

@requires-mongodb-3.6 @requires-replica-set
Scenario: Open a Change Stream resources and listen for "targettedDataNotificationsStream" message after POSTing a document. Only changes for documents that meets the changeStreamOperation's stage condition ($match) should be notified.

    # Establish WebSocket connection to get notified.
    Given def streamPath = '/_streams/changeStreamOperationWithStageParam/targettedDataNotificationsStream'
    And def baseUrl = 'http://localhost:18080'
    And def handler = function(notification) { java.lang.Thread.sleep(1000); karate.signal(notification) }
    And def host = baseUrl + coll + streamPath
    Then def socket = karate.webSocket(host, handler)
    

    Given path coll
    When request {}
    And method POST
    And def result = karate.listen(5000)
    Then match result == '#null'
    
    Given path coll
    When request {"targettedProperty": "test"}
    And method POST
    And def result = karate.listen(5000)
    Then match result == '#notnull'
    

# FAILURE SCENARIOS SECTION

@requires-mongodb-3.6 @requires-replica-set
Scenario: Performing a simple GET request on a Change Stream resource (Expected 400 Bad Request)

    Given path coll + '/_streams/changeStreamOperation/globalNotificationsStream'
    When method get
    Then status 400

@requires-mongodb-3.6 @requires-replica-set
Scenario:  GET the list of opened streams on a changeStreamOperation w/o opened streams (Expected 404 Not Found)
    Given path coll + '/_streams/emptyChangeStreamOperation'
    When method get
    Then status 404
