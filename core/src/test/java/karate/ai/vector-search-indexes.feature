@requires-vector-search
Feature: restheart-ai — vector search index management via /_indexes

# Part of the default suite: core/pom.xml starts mongodb/mongodb-atlas-local (mongod +
# mongot, self-initializing single-node replica set) for `mvn verify`, so
# $vectorSearch/createSearchIndexes are available without any extra setup. On CI legs
# that instead run the official mongo image (no $vectorSearch support — see
# RunnerIT.java and core/pom.xml's "mongodb-classic" profile), the @requires-vector-search
# tag excludes this feature via -Dkarate.vectorSearch=false, rather than @ignore.
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
#
# The suite's default-representation-format is HAL (conf-overrides.yml), which wraps
# GET /_indexes' array under _embedded['rh:index'] — so those GETs here pass ?rep=s
# (STANDARD) to get the plain array these assertions expect.

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
    And param rep = 's'
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
    And param rep = 's'
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
