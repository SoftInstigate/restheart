@requires-embedding-provider
Feature: restheart-ai — live embedding provider calls (Voyage AI)

# Exercises real HTTP calls to a configured Provider<EmbeddingModel> (Voyage AI),
# covering the integration points that document-chunking.feature and
# vector-search-indexes.feature deliberately leave untested: DocumentChunkingInterceptor
# actually attaching a `vector` field (plain and contextualized), AutoEmbeddingInterceptor
# embedding on write, and the $vectorize custom aggregation operator resolving inline.
#
# Requires a real Voyage AI API key in the VOYAGE_API_KEY environment variable (never
# committed to any file) plus -Dkarate.embeddingProvider=true. CI's atlas-local leg
# sets both automatically, but only when a VOYAGE_API_KEY secret is configured on the
# repository (see .github/workflows/*.yml) — otherwise this whole feature is skipped
# via the @requires-embedding-provider tag (see RunnerIT.java), same as any other run.
#
# Every scenario activates its provider via a PER-REQUEST override
# (?_ai-embedding-override=<providerName>, read by test-plugins'
# aiEmbeddingProviderOverrideInterceptor — see conf-overrides.yml), never via a
# suite-wide config change: restheart-ai's embedding plugins are enabled but have no
# static default provider/key, so only the one request carrying the query param ever
# triggers a live call. This keeps document-chunking.feature (and everything else in
# the suite) completely unaffected, and keeps live API usage — tokens and rate-limit
# budget — scoped to exactly what this feature needs.
#
# To run locally:
#   export VOYAGE_API_KEY=<your-key>
#   mvn clean verify -Dkarate.embeddingProvider=true -Dkarate.path=classpath:karate/ai/embedding-provider.feature
#
# The chunking/contextual scenarios upload a small fixed paragraph (small-doc.txt,
# ~125 characters) rather than the full RESTHeart.pdf used elsewhere, to keep the live
# embedding call — and its real token cost — minimal. Tika's ability to extract text
# from actual binary formats is already covered by document-chunking.feature; these
# scenarios only need to prove the `vector` field gets attached. Keep small-doc.txt at
# or under 200 characters (documentChunkingInterceptor's default chunk-overlap): with
# splitIntoChunks' overlap-driven re-chunking, anything longer produces 2+ chunks (and
# therefore 2+ times the embedded tokens) even though it still fits in one chunk-size.
#
# Assertions check "is a non-empty array of numbers", not an exact dimensionality —
# Voyage's embedding models support configurable output dimensions, so pinning a
# specific length would be an arbitrary coupling to a provider's current default.
#
# The suite's default-representation-format is HAL (conf-overrides.yml): the chunks
# collection GET passes ?rep=s (STANDARD) to get a plain array back; a single-document
# GET (auto-embedding scenario) needs no such param, its fields are exposed directly
# regardless of representation format; the aggregation GET ($vectorize scenario) keeps
# the suite's usual _embedded['rh:result'] shape (see aggregations/var-operator.feature).

Background:
    * url 'http://localhost:8080'
    * def adminAuth = 'Basic YWRtaW46c2VjcmV0'
    # a mongo collection POST returns 201 with an empty body and the new id only in the
    # Location header (an ObjectId, i.e. its last 24 characters) — see write-mode.feature
    * def idFromLocation = function(url) { return url.substring(url.length-24); }

Scenario: uploading a small text file produces a chunk with a real embedding vector
    * header Authorization = adminAuth
    Given path '/ai-test-embed-chunking'
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path '/ai-test-embed-chunking/docs.files'
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path '/ai-test-embed-chunking/docs.files?_ai-embedding-override=voyageEmbeddingProvider'
    And multipart file file = { read: 'small-doc.txt', filename: 'small-doc.txt' }
    And multipart field metadata = '{ "filename": "small-doc.txt" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    * header Authorization = adminAuth
    Given path '/ai-test-embed-chunking/_chunks'
    And param filter = '{"fileId": {"$oid": "' + fileId + '"}}'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length > 0
    And match each response[*].vector == '#array'
    And assert response[0].vector.length > 0

Scenario: writing a document to a vectorSearch-enabled collection triggers auto-embedding
    * header Authorization = adminAuth
    Given path '/ai-test-embed-auto'
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path '/ai-test-embed-auto/articles'
    And request { "vectorSearch": { "textField": "description", "embeddingField": "embedding" } }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path '/ai-test-embed-auto/articles?_ai-embedding-override=voyageEmbeddingProvider'
    And request { "description": "RESTHeart is a low-code API server for MongoDB" }
    When method POST
    Then status 201
    * def docId = idFromLocation(responseHeaders['Location'][0])

    * header Authorization = adminAuth
    Given path '/ai-test-embed-auto/articles/' + docId
    When method GET
    Then status 200
    And match response.embedding == '#array'
    And assert response.embedding.length > 0

Scenario: $vectorize resolves inline to a real embedding vector inside an aggregation pipeline
    * header Authorization = adminAuth
    Given path '/ai-test-embed-vectorize'
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path '/ai-test-embed-vectorize/docs'
    And request read('vectorize-def.json')
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path '/ai-test-embed-vectorize/docs'
    And request { "text": "hello world" }
    When method POST
    Then status 201

    * header Authorization = adminAuth
    Given path '/ai-test-embed-vectorize/docs/_aggrs/queryVector?_ai-embedding-override=voyageEmbeddingProvider'
    When method GET
    Then status 200
    * def vec = response._embedded['rh:result'][0].queryVector
    And match vec == '#array'
    And assert vec.length > 0

Scenario: documentChunkingInterceptor uses voyageContextualEmbeddingProvider via a per-request override
    * header Authorization = adminAuth
    Given path '/ai-test-embed-contextual'
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path '/ai-test-embed-contextual/docs.files'
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path '/ai-test-embed-contextual/docs.files?_ai-embedding-override=voyageContextualEmbeddingProvider'
    And multipart file file = { read: 'small-doc.txt', filename: 'small-doc.txt' }
    And multipart field metadata = '{ "filename": "small-doc.txt" }'
    When method POST
    Then status 201
    * def contextualFileId = idFromLocation(responseHeaders['Location'][0])

    * header Authorization = adminAuth
    Given path '/ai-test-embed-contextual/_chunks'
    And param filter = '{"fileId": {"$oid": "' + contextualFileId + '"}}'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length > 0
    And match each response[*].vector == '#array'
    And assert response[0].vector.length > 0
