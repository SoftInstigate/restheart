@ignore
Feature: Opens ChangesFeed resources

Background:
* url 'http://localhost:18080'
* def db = '/test-changes-feed'
* def coll = db + '/coll'

Scenario: Opens "globalNotificationsFeed" ChangesFeed to notify collection changes
    Given path coll + '/_feeds/feedOperation/globalNotificationsFeed'
    And request {}
    When method POST
    Then status 201

Scenario: Opens "toBeDeletedFeed" ChangesFeed for DELETE Scenario
    Given path coll + '/_feeds/feedOperation/toBeDeletedFeed'
    And request {}
    When method POST
    Then status 201

Scenario: Opens "targettedDataNotificationsFeed" ChangesFeed to notify collection changes for documents whose properties meets $match stage condition ({"targettedProperty": "test"}) 
    Given path coll + '/_feeds/feedOperationWithStageParam/targettedDataNotificationsFeed'
    And request {}
    And param avars = {"param": "test"}
    When method POST
    Then status 201
