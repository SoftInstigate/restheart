Feature: restheart-ai — embedding rules: one or more per collection (#752)

# A collection's vectorSearch metadata is a list of embedding rules, each embedding one text
# field into one vector field with its own provider and model; the object form of before is
# still read, as a list of one. Every rule here names one of test-plugins' fake providers
# (fakeEmbeddingProvider3d, fakeEmbeddingProvider5d: deterministic vectors, no network, see
# conf-overrides.yml), so the length of a vector says which provider embedded it. The live
# providers stay in embedding-provider.feature.
#
# Documents are written with PUT and a fixed _id, plus ?wm=upsert, so a rerun against a
# non-fresh MongoDB overwrites them in place.

Background:
    * url 'http://localhost:8080'
    * def db = '/ai-test-embedding-rules'
    * def adminAuth = 'Basic YWRtaW46c2VjcmV0'

    * header Authorization = adminAuth
    Given path db
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

Scenario: two rules on one collection get both vectors on a single write, each from its own provider
    * header Authorization = adminAuth
    Given path db + '/legal'
    And request
    """
    { "vectorSearch": [
        { "textField": "summary", "embeddingField": "summaryVector", "provider": "fakeEmbeddingProvider3d" },
        { "textField": "body",    "embeddingField": "bodyVector",    "provider": "fakeEmbeddingProvider5d" }
    ] }
    """
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/legal/both'
    And param wm = 'upsert'
    And request { "summary": "a short summary", "body": "the whole text of the document" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/legal/both'
    When method GET
    Then status 200
    And match response.summaryVector == '#[3] #number'
    And match response.bodyVector == '#[5] #number'

Scenario: a write that carries only one of the text fields embeds that one and leaves the other vector untouched
    * header Authorization = adminAuth
    Given path db + '/legal'
    And request
    """
    { "vectorSearch": [
        { "textField": "summary", "embeddingField": "summaryVector", "provider": "fakeEmbeddingProvider3d" },
        { "textField": "body",    "embeddingField": "bodyVector",    "provider": "fakeEmbeddingProvider5d" }
    ] }
    """
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/legal/partial'
    And param wm = 'upsert'
    And request { "summary": "first summary", "body": "the body" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/legal/partial'
    When method GET
    Then status 200
    * def bodyVectorBefore = response.bodyVector
    * def summaryVectorBefore = response.summaryVector

    # a PATCH with only the summary: summaryVector is recomputed, bodyVector is not touched
    * header Authorization = adminAuth
    Given path db + '/legal/partial'
    And request { "summary": "a different summary" }
    When method PATCH
    Then status 200

    * header Authorization = adminAuth
    Given path db + '/legal/partial'
    When method GET
    Then status 200
    And match response.bodyVector == bodyVectorBefore
    And match response.summaryVector == '#[3] #number'
    And match response.summaryVector != summaryVectorBefore

    # a document with no text field at all gets no vector and is otherwise written as it is
    * header Authorization = adminAuth
    Given path db + '/legal/none'
    And param wm = 'upsert'
    And request { "title": "no text to embed" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/legal/none'
    When method GET
    Then status 200
    And match response.summaryVector == '#notpresent'
    And match response.bodyVector == '#notpresent'

Scenario: a bulk write embeds every document, rule by rule
    * header Authorization = adminAuth
    Given path db + '/legal'
    And request
    """
    { "vectorSearch": [
        { "textField": "summary", "embeddingField": "summaryVector", "provider": "fakeEmbeddingProvider3d" },
        { "textField": "body",    "embeddingField": "bodyVector",    "provider": "fakeEmbeddingProvider5d" }
    ] }
    """
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/legal'
    And param wm = 'upsert'
    And request [ { "_id": "bulk1", "summary": "one", "body": "first body" }, { "_id": "bulk2", "body": "second body" } ]
    When method POST
    Then status 200

    * header Authorization = adminAuth
    Given path db + '/legal/bulk1'
    When method GET
    Then status 200
    And match response.summaryVector == '#[3] #number'
    And match response.bodyVector == '#[5] #number'

    * header Authorization = adminAuth
    Given path db + '/legal/bulk2'
    When method GET
    Then status 200
    And match response.summaryVector == '#notpresent'
    And match response.bodyVector == '#[5] #number'

Scenario: a failed embedding does not refuse the write: the document is stored without that vector, and the response warns
    * header Authorization = adminAuth
    Given path db + '/flaky'
    And request
    """
    { "vectorSearch": [
        { "textField": "summary", "embeddingField": "summaryVector", "provider": "fakeEmbeddingProvider3d" },
        { "textField": "body",    "embeddingField": "brokenVector",  "provider": "fakeEmbeddingProviderFailing" }
    ] }
    """
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/flaky/doc'
    And param wm = 'upsert'
    And request { "summary": "a summary", "body": "a body the vendor never embeds" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1
    And match response._warnings[0] contains "auto-embedding of 'brokenVector' failed"
    And match response._warnings[0] contains "the embedding vendor is unreachable"

    * header Authorization = adminAuth
    Given path db + '/flaky/doc'
    When method GET
    Then status 200
    And match response.body == 'a body the vendor never embeds'
    And match response.summaryVector == '#[3] #number'
    And match response.brokenVector == '#notpresent'

Scenario: a rule naming a provider that does not exist does not refuse the write either, and the response says why
    * header Authorization = adminAuth
    Given path db + '/unknown-provider'
    And request { "vectorSearch": { "textField": "text", "embeddingField": "vector", "provider": "noSuchEmbeddingProvider" } }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/unknown-provider/doc'
    And param wm = 'upsert'
    And request { "text": "some text" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1
    And match response._warnings[0] contains "auto-embedding of 'vector' skipped"
    And match response._warnings[0] contains "noSuchEmbeddingProvider"

    * header Authorization = adminAuth
    Given path db + '/unknown-provider/doc'
    When method GET
    Then status 200
    And match response.vector == '#notpresent'

Scenario: the object form, one rule not wrapped in a list, behaves exactly as before
    * header Authorization = adminAuth
    Given path db + '/articles'
    And request { "vectorSearch": { "textField": "description", "embeddingField": "embedding", "provider": "fakeEmbeddingProvider3d" } }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/articles/one'
    And param wm = 'upsert'
    And request { "description": "RESTHeart is a low-code API server for MongoDB" }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/articles/one'
    When method GET
    Then status 200
    And match response.embedding == '#[3] #number'

Scenario: two rules on the same vector field are refused when the metadata is written, naming the rule
    * header Authorization = adminAuth
    Given path db + '/invalid-duplicate'
    And request
    """
    { "vectorSearch": [
        { "textField": "summary", "embeddingField": "vector", "provider": "fakeEmbeddingProvider3d" },
        { "textField": "body",    "embeddingField": "vector", "provider": "fakeEmbeddingProvider5d" }
    ] }
    """
    When method PUT
    Then status 400
    And match response.message contains "vectorSearch[1]"
    And match response.message contains "'vector'"

Scenario: a rule missing textField or embeddingField is refused when the metadata is written, on PUT and on PATCH
    * header Authorization = adminAuth
    Given path db + '/invalid-missing'
    And request { "vectorSearch": [ { "textField": "summary", "embeddingField": "summaryVector" }, { "embeddingField": "bodyVector" } ] }
    When method PUT
    Then status 400
    And match response.message contains "vectorSearch[1]"
    And match response.message contains "textField"

    * header Authorization = adminAuth
    Given path db + '/invalid-missing'
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/invalid-missing'
    And request { "vectorSearch": { "textField": "summary" } }
    When method PATCH
    Then status 400
    And match response.message contains "embeddingField"

    * header Authorization = adminAuth
    Given path db + '/invalid-missing'
    And request { "vectorSearch": "yes" }
    When method PATCH
    Then status 400
    And match response.message contains "vectorSearch must be an object"
