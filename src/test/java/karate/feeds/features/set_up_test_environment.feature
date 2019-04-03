@ignore
Feature: Opens ChangesFeed resources

Background:
* url 'http://localhost:18080'
* def db = '/test-changes-feed'
* def coll = db + '/coll'
# note: db starting with 'test-' are automatically deleted after test finishes

@requires-mongodb-3.6 @requires-replica-set
Scenario: Setup test environment

# Step 1: Create test database
    Given path db
    And request {}
    When method PUT
    Then status 201

# Step 2: Create test collection
    Given path coll
    And request {"feeds": [{"stages": [], "uri": "feedOperation" }, {"stages": [], "uri": "emptyFeedOperation" }, {"stages": [{"_$match": {"fullDocument.targettedProperty": {"_$var": "param"}}}], "uri": "feedOperationWithStageParam" }]}
    When method PUT
    Then status 201

# Step 3: Open test feeds
    * call read('./features/open_feeds.feature')
