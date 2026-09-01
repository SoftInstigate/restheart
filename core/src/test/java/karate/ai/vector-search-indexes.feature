@ignore
Feature: restheart-ai — vector search index management via /_indexes

# Disabled by default — see RunnerIT's class javadoc for why and how to run this file.
# In short: needs MongoDB with $vectorSearch + createSearchIndexes support, e.g.:
#   docker run -p 27017:27017 -e DO_NOT_TRACK=1 mongodb/mongodb-atlas-local:preview
# then, with @ignore temporarily removed from this file:
#   mvn -pl core verify -Dit.test=RunnerIT -Dkarate.path=classpath:karate/ai
#
# Exercises VectorSearchIndexCreateInterceptor / ListInterceptor / DeleteInterceptor
# (ai/src/main/java/org/restheart/ai/interceptors/) — all three are enabled for the
# whole suite via conf-overrides.yml, but only these scenarios actually reach them.
#
# Uses a plain (non-autoEmbed) "vector" field type throughout: autoEmbed needs a
# Voyage AI key configured on mongot, which this suite has no reason to require just
# to verify RESTHeart's own request routing works.
#
# Index names are fixed (not per-run unique), matching the rest of the karate suite's
# convention of fixed db/collection names with no teardown — a rerun against a
# non-fresh MongoDB may fail the "create" scenarios on an index-already-exists error.
# Use a fresh container (or drop the ai-test-vidx db) between local reruns.

Background:
    * url 'http://localhost:8080'
    * def db = '/ai-test-vidx'
    * def coll = '/ai-test-vidx/articles'
    * def adminAuth = 'Basic YWRtaW46c2VjcmV0'

    * header Authorization = adminAuth
    Given path db
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path coll
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

Scenario: create a standard (non-autoEmbed) vector search index
    * header Authorization = adminAuth
    Given path coll + '/_indexes/article_vectors'
    And request { "type": "vectorSearch", "fields": [ { "type": "vector", "path": "embedding", "numDimensions": 8, "similarity": "cosine" } ] }
    When method PUT
    Then status 201

Scenario: a created index is listed with type=vectorSearch and its name as _id
    * header Authorization = adminAuth
    Given path coll + '/_indexes/list_test_index'
    And request { "type": "vectorSearch", "fields": [ { "type": "vector", "path": "embedding", "numDimensions": 8, "similarity": "cosine" } ] }
    When method PUT
    Then status 201

    * header Authorization = adminAuth
    Given path coll + '/_indexes'
    When method GET
    Then status 200
    * def vsIndex = karate.filter(response, function(x){ return x._id == 'list_test_index' })
    And match vsIndex[0].type == 'vectorSearch'

Scenario: deleting a vector search index removes it and returns 204
    * header Authorization = adminAuth
    Given path coll + '/_indexes/to_delete_index'
    And request { "type": "vectorSearch", "fields": [ { "type": "vector", "path": "embedding", "numDimensions": 8, "similarity": "cosine" } ] }
    When method PUT
    Then status 201

    * header Authorization = adminAuth
    Given path coll + '/_indexes/to_delete_index'
    When method DELETE
    Then status 204

    * header Authorization = adminAuth
    Given path coll + '/_indexes'
    When method GET
    Then status 200
    * def stillThere = karate.filter(response, function(x){ return x._id == 'to_delete_index' })
    And match stillThere == '#[0]'

Scenario: a request missing the required "fields" array is rejected with 400
    * header Authorization = adminAuth
    Given path coll + '/_indexes/bad_index'
    And request { "type": "vectorSearch" }
    When method PUT
    Then status 400
