@requires-vector-search
Feature: restheart-ai — $vectorize picks the rule of the $vectorSearch path (#753)

# The $vectorSearch half of what embedding-rules.feature proves for $vectorScan: with two
# embedding rules on one collection, $vectorize as the queryVector of a $vectorSearch embeds the
# question with the rule of the stage's path. Needs mongot, hence @requires-vector-search (see
# vector-search-indexes.feature and RunnerIT.java).
#
# The rules name test-plugins' fake providers, 3 and 5 long, no network (conf-overrides.yml), and
# each vector field gets an index of its own length. mongot refuses a query vector whose length is
# not the index's: a search that answers 200 with results proves the question was embedded by the
# rule of the searched field.
#
# Indexes have fixed names; they are dropped first, so a rerun against a non-fresh MongoDB starts
# clean. A new index needs a moment before it is queryable: a fixed wait, the same trade-off
# live-semantic-search.feature makes and explains.

Background:
    * url 'http://localhost:8080'
    * def db = '/ai-test-rules-vsearch'
    * def coll = '/ai-test-rules-vsearch/legal'
    * def adminAuth = 'Basic YWRtaW46c2VjcmV0'

Scenario: with two rules, $vectorize in a $vectorSearch embeds with the rule of the stage's path
    * header Authorization = adminAuth
    Given path db
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path coll
    And request
    """
    { "vectorSearch": [
        { "textField": "summary", "embeddingField": "summaryVector", "provider": "fakeEmbeddingProvider3d" },
        { "textField": "body",    "embeddingField": "bodyVector",    "provider": "fakeEmbeddingProvider5d" }
      ],
      "aggrs": [
        { "uri": "searchBody", "type": "pipeline", "stages": [
            { "$vectorSearch": { "index": "rules_body", "path": "bodyVector",
                "queryVector": { "$vectorize": { "$var": "q" } }, "numCandidates": 10, "limit": 5 } },
            { "$project": { "summaryVector": 0, "bodyVector": 0 } } ] },
        { "uri": "searchSummary", "type": "pipeline", "stages": [
            { "$vectorSearch": { "index": "rules_summary", "path": "summaryVector",
                "queryVector": { "$vectorize": { "$var": "q" } }, "numCandidates": 10, "limit": 5 } },
            { "$project": { "summaryVector": 0, "bodyVector": 0 } } ] }
      ] }
    """
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path coll + '/d1'
    And param wm = 'upsert'
    And request { "summary": "first summary", "body": "the first body" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path coll + '/d2'
    And param wm = 'upsert'
    And request { "summary": "second summary", "body": "the second body" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    # one index per vector field, each with its rule's length
    * header Authorization = adminAuth
    Given path coll + '/_indexes/rules_body'
    When method DELETE
    * header Authorization = adminAuth
    Given path coll + '/_indexes/rules_summary'
    When method DELETE

    * header Authorization = adminAuth
    Given path coll + '/_indexes/rules_body'
    And request { "type": "vectorSearch", "fields": [ { "type": "vector", "path": "bodyVector", "numDimensions": 5, "similarity": "cosine" } ] }
    When method PUT
    Then status 201

    * header Authorization = adminAuth
    Given path coll + '/_indexes/rules_summary'
    And request { "type": "vectorSearch", "fields": [ { "type": "vector", "path": "summaryVector", "numDimensions": 3, "similarity": "cosine" } ] }
    When method PUT
    Then status 201

    * eval Java.type('java.lang.Thread').sleep(10000)

    # bodyVector is indexed at 5: only a question embedded by the 5d rule is accepted
    * header Authorization = adminAuth
    Given path coll + '/_aggrs/searchBody'
    And param avars = '{"q": "a question"}'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length == 2

    # summaryVector is indexed at 3
    * header Authorization = adminAuth
    Given path coll + '/_aggrs/searchSummary'
    And param avars = '{"q": "a question"}'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length == 2
