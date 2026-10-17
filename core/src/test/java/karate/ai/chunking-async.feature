Feature: restheart-ai — chunking in the background (#758)

# With override-ai-chunking-async (attached here by aiChunkingOverrideInterceptor from
# ?_ai-chunking-async=true), the upload answers once the file is stored, and the chunker runs
# afterwards. What it did is written on the file's own document, as "chunking": pending before the
# upload answers, then done, failed or skipped. The suite's static configuration keeps the chunker
# synchronous, so every other feature is unaffected.
#
# No embedding here: the chunks collection has no vectorSearch rule. Files are read back with
# ?rep=s, since the suite's default representation is HAL.

Background:
    * url 'http://localhost:8080'
    * def db = '/ai-test-chunk-async'
    * def adminAuth = 'Basic YWRtaW46c2VjcmV0'
    * def idFromLocation = function(url) { return url.substring(url.length-24); }
    * def byOid = function(id) { return '{"_id": {"$oid": "' + id + '"}}'; }
    * def chunksOf = function(id) { return karate.call('classpath:karate/ai/chunks-of-db.feature', { db: db, coll: 'notes_chunks', filter: '{"fileId": {"$oid": "' + id + '"}}' }).chunks; }
    * def text = karate.repeat(40, function(i) { return 'Paragraph ' + i + ' of a note about the care of roses in spring.'; }).join('\n\n')
    * configure retry = { count: 40, interval: 250 }

    * header Authorization = adminAuth
    Given path db
    And request {}
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/notes.files'
    And request { "chunking": [ { "name": "notes", "target-collection": "notes_chunks", "chunk-size": 200, "chunk-overlap": 0, "splitter": "text" } ] }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

Scenario: the upload answers before the chunking, and the file says when it is done
    * header Authorization = adminAuth
    Given path db + '/notes.files'
    And param _ai-chunking-async = 'true'
    And multipart file file = { value: '#(text)', filename: 'roses.txt', contentType: 'text/plain' }
    And multipart field metadata = '{ "filename": "roses.txt" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    # waiting, running or already done: the status is there as soon as the upload has answered
    * header Authorization = adminAuth
    Given path db + '/notes.files'
    And param filter = byOid(fileId)
    And param rep = 's'
    When method GET
    Then status 200
    And match ['pending', 'running', 'done'] contains response[0].chunking.status

    * header Authorization = adminAuth
    Given path db + '/notes.files'
    And param filter = byOid(fileId)
    And param rep = 's'
    And retry until response[0].chunking.status == 'done'
    When method GET
    Then status 200
    And match response[0].chunking.rule == 'notes'
    And match response[0].chunking.warnings == []
    * def written = response[0].chunking.chunks
    * assert written > 1

    * def chunks = chunksOf(fileId)
    * match chunks == '#[' + written + ']'
    * match each chunks[*].rule == 'notes'

Scenario: one file of the database at a time, and both end done
    * def upload =
    """
    function(name) {
      var r = karate.call('classpath:karate/ai/chunking-async-upload.feature', { db: db, name: name, text: text });
      return r.fileId;
    }
    """
    * def first = upload('first.txt')
    * def second = upload('second.txt')

    * header Authorization = adminAuth
    Given path db + '/notes.files'
    And param filter = byOid(first)
    And param rep = 's'
    And retry until response[0].chunking.status == 'done'
    When method GET
    Then status 200

    * header Authorization = adminAuth
    Given path db + '/notes.files'
    And param filter = byOid(second)
    And param rep = 's'
    And retry until response[0].chunking.status == 'done'
    When method GET
    Then status 200

    * assert chunksOf(first).length > 1
    * assert chunksOf(second).length > 1

Scenario: a file that matches no rule is skipped
    * header Authorization = adminAuth
    Given path db + '/notes.files'
    And request { "chunking": [ { "name": "notes", "filter": { "extension": ".md" }, "target-collection": "notes_chunks" } ] }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/notes.files'
    And param _ai-chunking-async = 'true'
    And multipart file file = { value: '#(text)', filename: 'roses.txt', contentType: 'text/plain' }
    And multipart field metadata = '{ "filename": "roses.txt" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    * header Authorization = adminAuth
    Given path db + '/notes.files'
    And param filter = byOid(fileId)
    And param rep = 's'
    And retry until response[0].chunking.status == 'skipped'
    When method GET
    Then status 200
    * match chunksOf(fileId) == []

Scenario: without async the upload waits, and the status is written too
    * header Authorization = adminAuth
    Given path db + '/notes.files'
    And multipart file file = { value: '#(text)', filename: 'roses.txt', contentType: 'text/plain' }
    And multipart field metadata = '{ "filename": "roses.txt" }'
    When method POST
    Then status 201
    * def fileId = idFromLocation(responseHeaders['Location'][0])

    # the chunks are there as soon as the upload has answered
    * assert chunksOf(fileId).length > 1

    * header Authorization = adminAuth
    Given path db + '/notes.files'
    And param filter = byOid(fileId)
    And param rep = 's'
    When method GET
    Then status 200
    And match response[0].chunking.status == 'done'
    And match response[0].chunking.job == '#notpresent'

Scenario: a condition on the folder, with $regex on metadata.filename, is stored and applied
    # the operator travels inside the bucket's properties: it must survive being stored and read back
    * header Authorization = adminAuth
    Given path db + '/folders.files'
    And request { "chunking": [ { "name": "legal", "filter": { "metadata": { "filename": { "$regex": "^legals/" } } }, "target-collection": "notes_chunks", "chunk-size": 200, "chunk-overlap": 0, "splitter": "text" } ] }
    When method PUT
    Then assert [200, 201].indexOf(responseStatus) != -1

    * header Authorization = adminAuth
    Given path db + '/folders.files'
    And multipart file file = { value: '#(text)', filename: 'contratto.txt', contentType: 'text/plain' }
    And multipart field metadata = '{ "filename": "legals/contratto.txt" }'
    When method POST
    Then status 201
    * def legalId = idFromLocation(responseHeaders['Location'][0])
    * assert chunksOf(legalId).length > 1

    * header Authorization = adminAuth
    Given path db + '/folders.files'
    And multipart file file = { value: '#(text)', filename: 'project.txt', contentType: 'text/plain' }
    And multipart field metadata = '{ "filename": "design/project.txt" }'
    When method POST
    Then status 201
    * def designId = idFromLocation(responseHeaders['Location'][0])
    * match chunksOf(designId) == []

    * header Authorization = adminAuth
    Given path db + '/folders.files'
    And param filter = byOid(designId)
    And param rep = 's'
    When method GET
    Then status 200
    And match response[0].chunking.status == 'skipped'
