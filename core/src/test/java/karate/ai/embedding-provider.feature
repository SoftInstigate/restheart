@requires-embedding-provider
Feature: restheart-ai — live embedding provider calls (Voyage AI)

# Exercises real HTTP calls to a configured Provider<EmbeddingModel> (Voyage AI),
# covering the three integration points that document-chunking.feature and
# vector-search-indexes.feature deliberately leave untested: DocumentChunkingInterceptor
# actually attaching a `vector` field, AutoEmbeddingInterceptor embedding on write, and
# the $vectorize custom aggregation operator resolving inline.
#
# Requires a real Voyage AI API key wired in via the RHO environment variable (never
# committed to any file) plus -Dkarate.embeddingProvider=true. CI's atlas-local leg
# sets both automatically, but only when a VOYAGE_API_KEY secret is configured on the
# repository (see .github/workflows/*.yml) — otherwise this whole feature is skipped
# via the @requires-embedding-provider tag (see RunnerIT.java), same as any other run.
#
# To run locally:
#   export VOYAGE_API_KEY=<your-key>
#   export RHO="/voyageEmbeddingProvider/enabled->true;/voyageEmbeddingProvider/api-key->\"$VOYAGE_API_KEY\";/documentChunkingInterceptor/embedding-provider->\"voyageEmbeddingProvider\";/autoEmbeddingInterceptor/enabled->true;/autoEmbeddingInterceptor/embedding-provider->\"voyageEmbeddingProvider\";/vectorizeOperator/enabled->true;/vectorizeOperator/embedding-provider->\"voyageEmbeddingProvider\""
#   mvn clean verify -Dkarate.embeddingProvider=true -Dkarate.path=classpath:karate/ai/embedding-provider.feature
#
# Assertions check "is a non-empty array of numbers", not an exact dimensionality —
# voyage-3.5 supports configurable output dimensions, so pinning a specific length
# would be an arbitrary coupling to Voyage's current default.
#
# The suite's default-representation-format is HAL (conf-overrides.yml): the chunks
# collection GET passes ?rep=s (STANDARD) to get a plain array back; a single-document
# GET (auto-embedding scenario) needs no such param, its fields are exposed directly
# regardless of representation format; the aggregation GET ($vectorize scenario) keeps
# the suite's usual _embedded['rh:result'] shape (see aggregations/var-operator.feature).
#
# The last scenario (voyageContextualEmbeddingProvider) does NOT need the RHO string
# above: voyageContextualEmbeddingProvider is already enabled in conf-overrides.yml
# with no static api-key, and aiVoyageContextualOverrideInterceptor
# (test-plugins) attaches override-ai-embedding-provider + the API key (from the same
# VOYAGE_API_KEY env var) per-request when the request carries
# ?_ai-voyage-contextual-override=1 — so only that one request resolves to it. Just:
#   export VOYAGE_API_KEY=<your-key>
#   mvn clean verify -Dkarate.embeddingProvider=true -Dkarate.path=classpath:karate/ai/embedding-provider.feature

Background:
    * url 'http://localhost:8080'
    * def adminAuth = 'Basic YWRtaW46c2VjcmV0'
    # a mongo collection POST returns 201 with an empty body and the new id only in the
    # Location header (an ObjectId, i.e. its last 24 characters) — see write-mode.feature
    * def idFromLocation = function(url) { return url.substring(url.length-24); }

Scenario: uploading a text-extractable file produces chunks with a real embedding vector
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
    Given path '/ai-test-embed-chunking/docs.files'
    And multipart file file = { read: '../RESTHeart.pdf', filename: 'RESTHeart.pdf' }
    And multipart field metadata = '{ "filename": "RESTHeart.pdf" }'
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
    Given path '/ai-test-embed-auto/articles'
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
    Given path '/ai-test-embed-vectorize/docs/_aggrs/queryVector'
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

    # only this request carries the override — every other request in the suite
    # (including the other scenarios above) never resolves to this provider. Inlined
    # in the path rather than via a separate `param` step, matching how the other
    # hyphenated-qparam test interceptors are called (see test-plugins' sibling
    # *OverrideInterceptor classes and their karate usages).
    * header Authorization = adminAuth
    Given path '/ai-test-embed-contextual/docs.files?_ai-voyage-contextual-override=1'
    And multipart file file = { read: '../RESTHeart.pdf', filename: 'RESTHeart.pdf' }
    And multipart field metadata = '{ "filename": "RESTHeart.pdf" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    * header Authorization = adminAuth
    Given path '/ai-test-embed-contextual/_chunks'
    And param filter = '{"fileId": {"$oid": "' + fileId + '"}}'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length > 0
    And match each response[*].vector == '#array'
    And assert response[0].vector.length > 0
