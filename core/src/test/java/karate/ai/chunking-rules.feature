Feature: restheart-ai — chunking rules per GridFS bucket (#754)

# A bucket says in its chunking metadata which files are chunked, how, and where the chunks go; the
# first rule a file matches is applied, a file that matches none is not chunked, and a bucket without
# chunking is never touched. The chunker computes no vectors: it writes all the chunks of a file in
# one request, and the target collection embeds them with its own vectorSearch rules.
#
# The target collections use test-plugins' fake providers, no network: fakeEmbeddingProvider3d and
# 5d give vectors 3 and 5 long, so the length says which collection embedded a chunk;
# fakeContextualEmbeddingProvider answers [group size, position], so its vectors say whether a
# file's chunks were embedded together. Chunks are read back by their file, so a rerun against a
# non-fresh MongoDB counts only its own.

Background:
    * url 'http://localhost:8080'
    * def db = '/ai-test-chunk-rules'
    * def adminAuth = 'Basic YWRtaW46c2VjcmV0'
    * def testAuth = 'Basic dGVzdDpzZWNyZXQ='
    * def idFromLocation = function(url) { return url.substring(url.length-24); }
    * def chunksOf =
    """
    function(coll, filter) {
      return karate.call('classpath:karate/ai/chunks-of.feature', { coll: coll, filter: filter }).chunks;
    }
    """
    * def byOid = function(id) { return '{"fileId": {"$oid": "' + id + '"}}'; }

    * header Authorization = adminAuth
    Given path db
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    # each chunks collection embeds with its own rule; fresh_chunks is left for the chunker to create
    * header Authorization = adminAuth
    Given path db + '/manuals_chunks'
    And request { "vectorSearch": { "textField": "text", "embeddingField": "vector", "provider": "fakeEmbeddingProvider3d" } }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/code_chunks'
    And request { "vectorSearch": { "textField": "text", "embeddingField": "vector", "provider": "fakeEmbeddingProvider5d" } }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/tagged_chunks'
    And request { "vectorSearch": { "textField": "text", "embeddingField": "vector", "provider": "fakeContextualEmbeddingProvider", "groupBy": "fileId" } }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/docs.files'
    And request
    """
    { "chunking": [
        { "name": "tagged", "filter": { "metadata": { "kind": "manual" } },
          "target-collection": "tagged_chunks", "chunk-size": 60, "chunk-overlap": 10, "splitter": "text" },
        { "name": "manuals", "filter": { "contentType": ["application/pdf", "application/vnd.openxmlformats-officedocument.*"] },
          "target-collection": "manuals_chunks" },
        { "name": "code", "filter": { "extension": [".java", ".ts", ".py"] }, "target-collection": "code_chunks" },
        { "name": "fresh", "filter": { "extension": ".md" }, "target-collection": "fresh_chunks" }
    ] }
    """
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

Scenario: a PDF goes to the manuals rule, and its chunks carry the file's name, type and metadata
    * header Authorization = adminAuth
    Given path db + '/docs.files'
    And multipart file file = { read: '../RESTHeart.pdf', filename: 'RESTHeart.pdf' }
    And multipart field metadata = '{ "filename": "RESTHeart.pdf", "owner": "docs-team" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    * def chunks = chunksOf('manuals_chunks', byOid(fileId))
    * assert chunks.length > 0
    * match each chunks[*].rule == 'manuals'
    * match each chunks[*].vector == '#[3] #number'
    * match each chunks[*].filename == 'RESTHeart.pdf'
    * match each chunks[*].contentType == 'application/pdf'
    * match each chunks[*].metadata.owner == 'docs-team'
    * match each chunks[*].source == 'ai-test-chunk-rules/docs.files/' + fileId
    * def inCode = chunksOf('code_chunks', byOid(fileId))
    * match inCode == '#[0]'

Scenario: a source file goes to the code rule, embedded by its collection's model
    * header Authorization = adminAuth
    Given path db + '/docs.files'
    And multipart file file = { value: 'public class Hello {\n  public static void main(String[] a) { System.out.println("hi"); }\n}\n', filename: 'Hello.java', contentType: 'text/x-java-source' }
    And multipart field metadata = '{ "filename": "Hello.java" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    * def chunks = chunksOf('code_chunks', byOid(fileId))
    * assert chunks.length > 0
    * match each chunks[*].rule == 'code'
    * match each chunks[*].vector == '#[5] #number'

Scenario: a file no rule matches is not chunked
    * header Authorization = adminAuth
    Given path db + '/docs.files'
    And multipart file file = { value: 'not really a picture, and no rule wants it', filename: 'picture.png', contentType: 'image/png' }
    And multipart field metadata = '{ "filename": "picture.png" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    * def a = chunksOf('manuals_chunks', byOid(fileId))
    * def b = chunksOf('code_chunks', byOid(fileId))
    * def c = chunksOf('tagged_chunks', byOid(fileId))
    * def d = chunksOf('fresh_chunks', byOid(fileId))
    * match a == '#[0]'
    * match b == '#[0]'
    * match c == '#[0]'
    * match d == '#[0]'

Scenario: a metadata filter routes by a field set at upload, first match wins, and groupBy embeds a file's chunks together
    * header Authorization = adminAuth
    Given path db + '/docs.files'
    And multipart file file = { value: 'The first paragraph of the manual explains the setup. The second one explains the maintenance. The third one lists the spare parts.', filename: 'notes.txt', contentType: 'text/plain' }
    And multipart field metadata = '{ "filename": "notes.txt", "kind": "manual" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    * def chunks = chunksOf('tagged_chunks', byOid(fileId))
    * assert chunks.length > 1
    * match each chunks[*].rule == 'tagged'
    * match each chunks[*].metadata.kind == 'manual'
    # every chunk says it was embedded in a group as large as the file's chunks, at its own position
    * def together = chunks.every(function(c){ return c.vector[0] == chunks.length && c.vector[1] == c.chunkIndex })
    * assert together

    # a .java would match the code rule too: the tagged rule comes first
    * header Authorization = adminAuth
    Given path db + '/docs.files'
    And multipart file file = { value: 'class Manual { }\n', filename: 'Manual.java', contentType: 'text/x-java-source' }
    And multipart field metadata = '{ "filename": "Manual.java", "kind": "manual" }'
    When method POST
    Then status 201
    * def javaId = idFromLocation(responseHeaders['Location'][0])
    * def tagged = chunksOf('tagged_chunks', byOid(javaId))
    * def code = chunksOf('code_chunks', byOid(javaId))
    * assert tagged.length > 0
    * match code == '#[0]'

Scenario: a target collection that does not exist is created, and without an embedding rule the chunks have no vector
    * header Authorization = adminAuth
    Given path db + '/docs.files'
    And multipart file file = { value: '# Readme\nA short readme.', filename: 'readme.md', contentType: 'text/markdown' }
    And multipart field metadata = '{ "filename": "readme.md" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    * def chunks = chunksOf('fresh_chunks', byOid(fileId))
    * assert chunks.length > 0
    # no chunk has a vector: `match each` on chunks[*].vector would be an empty list, which Karate fails
    * def withVector = chunks.filter(function(c){ return c.vector })
    * match withVector == '#[0]'

Scenario: a user who may write to the bucket but not to the chunks collection gets the chunks all the same
    * header Authorization = testAuth
    Given path db + '/manuals_chunks'
    When method GET
    Then status 403

    * header Authorization = testAuth
    Given path db + '/docs.files'
    And multipart file file = { read: '../RESTHeart.pdf', filename: 'RESTHeart.pdf' }
    And multipart field metadata = '{ "filename": "RESTHeart.pdf" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    * def chunks = chunksOf('manuals_chunks', byOid(fileId))
    * assert chunks.length > 0
    * match each chunks[*].vector == '#[3] #number'

Scenario: replacing a file replaces its chunks, deleting it deletes them
    * def bySource = '{"source": "ai-test-chunk-rules/docs.files/replace-me"}'

    * header Authorization = adminAuth
    Given path db + '/docs.files/replace-me'
    And param wm = 'upsert'
    And multipart file file = { value: 'alpha version of the text', filename: 'replace-me.md', contentType: 'text/markdown' }
    And multipart field metadata = '{ "filename": "replace-me.md" }'
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/docs.files/replace-me'
    And param wm = 'upsert'
    And multipart file file = { value: 'omega version of the text', filename: 'replace-me.md', contentType: 'text/markdown' }
    And multipart field metadata = '{ "filename": "replace-me.md" }'
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * def chunks = chunksOf('fresh_chunks', bySource)
    * assert chunks.length > 0
    * match each chunks[*].text contains 'omega'

    * header Authorization = adminAuth
    Given path db + '/docs.files/replace-me'
    When method DELETE
    Then status 204

    * def gone = chunksOf('fresh_chunks', bySource)
    * match gone == '#[0]'

Scenario: a bulk delete of files deletes their chunks
    * def batch = 'batch-' + java.util.UUID.randomUUID()

    * header Authorization = adminAuth
    Given path db + '/docs.files'
    And multipart file file = { value: 'first file of the batch', filename: 'one.md', contentType: 'text/markdown' }
    And multipart field metadata = '{ "filename": "one.md", "batch": "' + batch + '" }'
    When method POST
    Then status 201

    * header Authorization = adminAuth
    Given path db + '/docs.files'
    And multipart file file = { value: 'second file of the batch', filename: 'two.md', contentType: 'text/markdown' }
    And multipart field metadata = '{ "filename": "two.md", "batch": "' + batch + '" }'
    When method POST
    Then status 201

    * def byBatch = '{"metadata.batch": "' + batch + '"}'
    * def before = chunksOf('fresh_chunks', byBatch)
    * assert before.length >= 2

    * header Authorization = adminAuth
    Given path db + '/docs.files/*'
    And param filter = '{"metadata.batch": "' + batch + '"}'
    When method DELETE
    Then status 200

    * def after = chunksOf('fresh_chunks', byBatch)
    * match after == '#[0]'

Scenario: a bucket without chunking rules is left alone
    * header Authorization = adminAuth
    Given path db + '/plain.files'
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/plain.files'
    And multipart file file = { read: '../RESTHeart.pdf', filename: 'RESTHeart.pdf' }
    And multipart field metadata = '{ "filename": "RESTHeart.pdf" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    * def a = chunksOf('manuals_chunks', byOid(fileId))
    * def b = chunksOf('_chunks', byOid(fileId))
    * match a == '#[0]'
    * match b == '#[0]'

Scenario: invalid rules are refused when the bucket metadata is written, naming the rule
    * header Authorization = adminAuth
    Given path db + '/invalid.files'
    And request { "chunking": [ { "name": "ok", "target-collection": "c" }, { "name": "no target" } ] }
    When method PUT
    Then status 400
    And match response.message contains 'chunking[1]'
    And match response.message contains 'target-collection'

    * header Authorization = adminAuth
    Given path db + '/invalid.files'
    And request { "chunking": [ { "target-collection": "c", "filter": { "mime": "application/pdf" } } ] }
    When method PUT
    Then status 400
    And match response.message contains "unknown filter key 'mime'"

    # the model belongs to the chunks collection's embedding rule
    * header Authorization = adminAuth
    Given path db + '/invalid.files'
    And request { "chunking": [ { "target-collection": "c", "model": "voyage-context-4" } ] }
    When method PUT
    Then status 400
    And match response.message contains 'vectorSearch rules of the target collection'
