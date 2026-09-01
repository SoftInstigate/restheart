Feature: restheart-ai — $vectorScan brute-force vector search

# $vectorScan needs no mongot, no Atlas, and no vector search index of any kind — it
# computes distances directly against a plain array field. Unlike vector-search-indexes.
# feature and embedding-provider.feature, this runs on *every* CI leg, official-mongo
# included: it has no dependency this suite can't already satisfy everywhere. See
# restheart/#712 and VectorScanInterceptor's javadoc for the full design.
#
# All documents are written with PUT and a fixed _id (not POST), so a rerun against a
# non-fresh MongoDB overwrites them in place instead of accumulating duplicates. PUT on
# a document that doesn't exist yet is a 404 with RESTHeart's default write mode, so
# these also pass ?wm=upsert to create-or-replace.
#
# Query vector is always [1, 0]; document vectors are plain (non-unit) vectors chosen so
# cosine similarity comes out to clean, strictly distinct values — cosine normalizes
# internally, so magnitude doesn't matter, only direction:
#   docA = [10, 0]  -> cosine 1.0    (category x)
#   docD = [9, 4]   -> cosine ~0.914 (category y) -- would outrank docB if not filtered
#   docB = [8, 6]   -> cosine 0.8    (category x)
#   docC = [0, 10]  -> cosine 0.0    (category y)
#
# The suite's default-representation-format is HAL (conf-overrides.yml), which wraps an
# aggregation GET's array under _embedded['rh:result'] — so these GETs pass ?rep=s
# (STANDARD) to get the plain array these assertions expect, same as the other ai
# feature files.

Background:
    * url 'http://localhost:8080'
    * def db = '/ai-test-vectorscan'
    * def coll = '/ai-test-vectorscan/articles'
    * def adminAuth = 'Basic YWRtaW46c2VjcmV0'

    * header Authorization = adminAuth
    Given path db
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path coll
    And request read('vectorscan-def.json')
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path coll + '/docA'
    And param wm = 'upsert'
    And request { "vector": [10, 0], "category": "x" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path coll + '/docB'
    And param wm = 'upsert'
    And request { "vector": [8, 6], "category": "x" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path coll + '/docC'
    And param wm = 'upsert'
    And request { "vector": [0, 10], "category": "y" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path coll + '/docD'
    And param wm = 'upsert'
    And request { "vector": [9, 4], "category": "y" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

Scenario: results are ordered by similarity, most similar first, each carrying a score
    * header Authorization = adminAuth
    Given path coll + '/_aggrs/scanAll'
    And param avars = '{"q": [1, 0], "k": 10}'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length == 4
    And match response[0]._id == 'docA'
    And match response[1]._id == 'docD'
    And match response[2]._id == 'docB'
    And match response[3]._id == 'docC'
    And match each response[*].score == '#number'
    And assert response[0].score > response[1].score
    And assert response[1].score > response[2].score
    And assert response[2].score > response[3].score

Scenario: limit truncates to the top-K results
    * header Authorization = adminAuth
    Given path coll + '/_aggrs/scanAll'
    And param avars = '{"q": [1, 0], "k": 2}'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length == 2
    And match response[0]._id == 'docA'
    And match response[1]._id == 'docD'

Scenario: a real $match stage before $vectorScan filters candidates with full MQL, not a restricted filter
    * header Authorization = adminAuth
    Given path coll + '/_aggrs/scanFiltered'
    And param avars = '{"q": [1, 0], "k": 10}'
    And param rep = 's'
    When method GET
    Then status 200
    # docD would outrank docB unfiltered (~0.914 vs 0.8) -- excluded here by category,
    # proving the $match actually ran against MongoDB, not just trimmed the tail
    And assert response.length == 2
    And match response[0]._id == 'docA'
    And match response[1]._id == 'docB'
    And match each response[*].category == 'x'

Scenario: stages after $vectorScan run for real, via the $documents bridge
    * header Authorization = adminAuth
    Given path coll + '/_aggrs/scanProjected'
    And param avars = '{"q": [1, 0], "k": 10}'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length == 4
    And match response[0] == { "category": "x", "score": "#number" }
    And match response[0].vector == '#notpresent'
    And match response[0]._id == '#notpresent'

Scenario: a $vectorScan stage missing the required 'path' is rejected with 400
    * header Authorization = adminAuth
    Given path coll + '/_aggrs/scanMissingPath'
    And param avars = '{"q": [1, 0]}'
    When method GET
    Then status 400
