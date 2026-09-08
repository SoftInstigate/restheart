@requires-vector-search @requires-embedding-provider
Feature: restheart-ai — full live journey: real embeddings, $vectorSearch, real rerank

# The complete, real round trip: write documents that get embedded by a live call to
# Voyage AI, index the resulting vectors with a genuine $vectorSearch index (needs
# mongot — hence @requires-vector-search, on top of @requires-embedding-provider), query
# with $vectorize resolving the search text into a vector inline, and rerank the results
# with a live call to a Voyage rerank model. Every other karate/ai/*.feature exercises
# one piece of this in isolation (chunking, a single provider call, $vectorScan without
# mongot); this is the only one that chains all of them together the way a real
# deployment would.
#
# Runs only when BOTH gates are open: karate.vectorSearch=true (the atlas-local CI leg)
# AND karate.embeddingProvider=true (a VOYAGE_API_KEY secret/env var is present) — see
# RunnerIT.java. To run locally:
#   export VOYAGE_API_KEY=<your-key>
#   mvn clean verify -Dkarate.embeddingProvider=true -Dkarate.path=classpath:karate/ai/live-semantic-search.feature
#
# Embedding/rerank providers are activated per-request via aiEmbeddingProviderOverrideInterceptor
# (?_ai-embedding-override=voyageEmbeddingProvider, ?_ai-rerank-override=voyageRerankProvider)
# — see embedding-provider.feature's header for why per-request overrides instead of a
# suite-wide config change. Exactly 2 live embedding calls happen here (one batched call
# for all 3 documents, one for the query text) plus 1 live rerank call — kept to short
# sentences to keep token usage minimal, same reasoning as embedding-provider.feature.
#
# The suite's default-representation-format is HAL: GETs pass ?rep=s (STANDARD) for a
# plain array/object back, same as the other ai feature files.
#
# Assertions only pin down the one document that is unambiguously the most relevant
# result (about electric cars, for a query about electric vehicles) — the relative
# order of the other two (unrelated topics) is not asserted, since that depends on a
# live model's exact behavior, not on anything this test is meant to verify.

Background:
    * url 'http://localhost:8080'
    * def db = '/ai-test-live-search'
    * def coll = '/ai-test-live-search/docs'
    * def adminAuth = 'Basic YWRtaW46c2VjcmV0'

Scenario: write documents with a live embedding, index them, query with $vectorSearch, rerank live
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

    * header Authorization = adminAuth
    Given path coll
    And request { "vectorSearch": { "textField": "text", "embeddingField": "embedding" } }
    When method PATCH
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path coll
    And request read('live-search-def.json')
    When method PATCH
    Then assert [200, 201].indexOf(responseStatus) != -1

    # one batched live embedding call for all 3 documents
    * def docs =
    """
    [
      { "text": "Electric cars use rechargeable batteries and electric motors instead of a gasoline engine." },
      { "text": "The Great Wall of China is an ancient series of fortifications stretching thousands of kilometers." },
      { "text": "Sourdough bread is made by fermenting dough using naturally occurring lactobacilli and yeast." }
    ]
    """
    * header Authorization = adminAuth
    Given path coll
    And param _ai-embedding-override = 'voyageEmbeddingProvider'
    And request docs
    When method POST
    Then assert [200, 201].indexOf(responseStatus) != -1

    # read the real embedding dimension back rather than hardcoding a model-specific
    # number that could change if the default model changes
    * header Authorization = adminAuth
    Given path coll
    And param rep = 's'
    And param pagesize = 1
    When method GET
    Then status 200
    * def dim = response[0].embedding.length
    * assert dim > 0

    * header Authorization = adminAuth
    Given path coll + '/_indexes/live_vectors'
    * def indexBody = { type: 'vectorSearch', fields: [ { type: 'vector', path: 'embedding', numDimensions: '#(dim)', similarity: 'cosine' } ] }
    And request indexBody
    When method PUT
    Then status 201

    # a freshly created Atlas Search index needs a little time to build before it's
    # queryable — this only runs on one gated CI leg, so a generous fixed wait is a
    # simpler and safer trade-off than a hand-rolled polling loop. karate.pause() is
    # NOT a plain blocking sleep -- it's paired with proceed()/stop() for Karate's
    # interactive debugger UI and is a no-op in a headless run (confirmed: index
    # creation and the next request were ~10ms apart in a real run, not ~10s). Use
    # direct GraalJS Java interop instead, same pattern already used elsewhere in this
    # suite (Java.type('java.util.Base64') in aggregations/var-operator.feature) --
    # java.lang.Thread.sleep is guaranteed to actually block.
    * eval Java.type('java.lang.Thread').sleep(10000)

    # one live embedding call for the query text, one live rerank call for the results
    * header Authorization = adminAuth
    Given path coll + '/_aggrs/semantic-search'
    And param avars = '{"q": "electric vehicles and battery technology"}'
    And param _ai-embedding-override = 'voyageEmbeddingProvider'
    And param _ai-rerank-override = 'voyageRerankProvider'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length == 2
    And match response[0].text contains 'Electric cars'
