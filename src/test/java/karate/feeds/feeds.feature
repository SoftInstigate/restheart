Feature: test changesFeed

Background:
* url 'http://localhost:18080'
* def db = '/test-changes-feed'
* def coll = db + '/coll'
* callonce read('./features/set_up_test_environment.feature')

# SUCCESSFULL SCENARIOS SECTION

@requires-mongodb-3.6 @requires-replica-set
Scenario: Delete a ChangesFeed resource.
    Given path coll + '/_feeds/feedOperation/toBeDeletedFeed'
    When method delete
    Then status 204

@requires-mongodb-3.6 @requires-replica-set
Scenario: GET the ChangesFeed resources associated to given feedOperation
    Given path coll + '/_feeds/feedOperation'
    When method get
    Then status 200

@requires-mongodb-3.6 @requires-replica-set
Scenario: Open ChangesFeeds resources and listen for "globalNotificationsFeed" ws message after POSTing a document.
    * def feedPath = '/_feeds/feedOperation/globalNotificationsFeed'
    * def baseUrl = 'http://localhost:18080'
    * def handler = function(notification) { java.lang.Thread.sleep(1000); karate.signal(notification) }
    * def host = baseUrl + coll + feedPath    
    
    # Establish WebSocket connection to get notified.
    * def socket = karate.webSocket(host, handler)

    Given path coll
    And request {}

    When method POST
    And def result = karate.listen(5000)
    Then match result == '#notnull'

@requires-mongodb-3.6 @requires-replica-set
Scenario: Open a ChangesFeed resources and listen for "targettedDataNotificationsFeed" message after POSTing a document. Only changes for documents that meets the feedOperation's stage condition ($match) should be notified.

    * def feedPath = '/_feeds/feedOperationWithStageParam/targettedDataNotificationsFeed'
    * def baseUrl = 'http://localhost:18080'
    * def handler = function(notification) { java.lang.Thread.sleep(1000); karate.signal(notification) }
    * def host = baseUrl + coll + feedPath

    
    # Establish WebSocket connection to get notified.
    * def socket = karate.webSocket(host, handler)

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
Scenario: Performing a simple GET request on a ChangeFeed resource (Expected 400 Bad Request)

    Given path coll + '/_feeds/feedOperation/globalNotificationsFeed'
    When method get
    Then status 400

@requires-mongodb-3.6 @requires-replica-set
Scenario:  GET the list of running feeds on a FeedOperation w/o opened feeds (Expected 404 Not Found)
    Given path coll + '/_feeds/emptyFeedOperation'
    When method get
    Then status 404
