Feature: restheart-ai — document chunking on GridFS upload

# Part of the default suite. Deliberately NOT tagged @requires-vector-search (unlike
# vector-search-indexes.feature): this one only needs Tika text extraction + GridFS,
# which work against any MongoDB — so it also runs on CI's official-mongo
# compatibility-matrix legs (see RunnerIT.java), for extra coverage at no cost.
#
# Exercises DocumentChunkingInterceptor (ai/src/main/java/org/restheart/ai/interceptors/),
# enabled for the whole suite via conf-overrides.yml with its code defaults
# (chunk-size: 1000, chunk-overlap: 200, target-collection: _chunks).
#
# Scenarios filter _chunks by fileId (not just "collection is non-empty") so a rerun
# against a db left over from a previous run doesn't produce a false pass/fail.

Background:
    * url 'http://localhost:8080'
    * def db = '/ai-test-chunking'
    * def bucket = '/ai-test-chunking/docs.files'
    * def chunksColl = '/ai-test-chunking/_chunks'
    * def adminAuth = 'Basic YWRtaW46c2VjcmV0'

    * header Authorization = adminAuth
    Given path db
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path bucket
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

Scenario: uploading a text-extractable file triggers chunking into the target collection
    * header Authorization = adminAuth
    Given path bucket
    And multipart file file = { read: 'RESTHeart.pdf', filename: 'RESTHeart.pdf' }
    And multipart field metadata = '{ "filename": "RESTHeart.pdf" }'
    When method POST
    Then status 201
    * def fileId = response._id

    # DocumentChunkingInterceptor runs at RESPONSE, synchronously — the chunks
    # collection is already populated by the time the upload request returns.
    * header Authorization = adminAuth
    Given path chunksColl
    And param filter = '{"fileId": {"$oid": "' + fileId + '"}}'
    When method GET
    Then status 200
    And assert response.length > 0
    And match each response[*].text == '#string'
    And match each response[*].source == '#string'
    And match response[0].chunkIndex == 0

Scenario: an upload with no extractable text does not create any chunks
    * header Authorization = adminAuth
    Given path bucket
    And multipart file file = { value: '', filename: 'empty.txt', contentType: 'text/plain' }
    And multipart field metadata = '{ "filename": "empty.txt" }'
    When method POST
    Then status 201
    * def emptyFileId = response._id

    * header Authorization = adminAuth
    Given path chunksColl
    And param filter = '{"fileId": {"$oid": "' + emptyFileId + '"}}'
    When method GET
    Then status 200
    And match response == '#[0]'
