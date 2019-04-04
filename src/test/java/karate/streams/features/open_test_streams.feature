@ignore
Feature: Opens Change Stream resources

Background:
* url 'http://localhost:18080'
* def db = '/test-change-streams'
* def coll = db + '/coll'

Scenario: Opens "globalNotificationsStream" Change Stream to notify collection changes
    Given path coll + '/_streams/changeStreamOperation/globalNotificationsStream'
    And request {}
    When method POST
    Then status 201

Scenario: Opens "toBeDeletedStream" Change Stream for DELETE Scenario
    Given path coll + '/_streams/changeStreamOperation/toBeDeletedStream'
    And request {}
    When method POST
    Then status 201

Scenario: Opens "targettedDataNotificationsStream" Change Stream to notify collection changes for documents whose properties meets $match stage condition ({"targettedProperty": "test"}) 
    Given path coll + '/_streams/changeStreamOperationWithStageParam/targettedDataNotificationsStream'
    And request {}
    And param avars = {"param": "test"}
    When method POST
    Then status 201
