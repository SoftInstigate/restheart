Feature: Test Change Streams

Background:
* url 'http://localhost:8080'
* def db = '/test-change-streams'
* def coll = db + '/coll'
* callonce read('./features/set_up_test_environment.feature')

# SUCCESSFULL SCENARIOS SECTION

@requires-mongodb-3.6 @requires-replica-set
Scenario: Connect to a change stream resources w/o stage paramethers and listen for WebSocket message after POSTing a document.


    # Establish WebSocket connection to get notified.
    Given def streamPath = '/_streams/changeStream'
    And def baseUrl = 'http://localhost:8080'
    And def handler = function(notification) { karate.signal(notification) }
    And def host = baseUrl + encodeURI(coll + streamPath)
    Then def socket = karate.webSocket(host, handler)
    
    Given path coll
    And request {}
    When method POST
    And def result = karate.listen(5000)
    Then match result == '#notnull'

@requires-mongodb-3.6 @requires-replica-set
Scenario: Connect to a change stream resources w/ stage paramethers and listen for WebSocket message after POSTing a document. Only changes for documents that meets the change stream's stage condition ($match) should be notified.

    # Establish WebSocket connection to get notified.
    Given def streamPath = '/_streams/changeStreamWithStageParam?avars={\'param\': \'test\'}'
    And def baseUrl = 'http://localhost:8080'
    And def handler = function(notification) { karate.signal(notification) }
    And def host = baseUrl + encodeURI(coll + streamPath)
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

    Given path coll + '/_streams/changeStream'
    When method get
    Then status 400

