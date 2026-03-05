Feature: Test GridFS bulk delete (DELETE /db/bucket.files/*)

Background:
* url 'http://localhost:8080'
* def db = '/test-gridfs-bulk-delete'
* def bucket = '/test-gridfs-bulk-delete/fs.files'
* def auth = 'Basic YWRtaW46c2VjcmV0'

Scenario: Create test db and bucket
    * header Authorization = auth
    Given path db
    And request { }
    When method PUT
    Then status 201

    * header Authorization = auth
    Given path bucket
    And request { }
    When method PUT
    Then status 201

Scenario: Bulk delete files matching a filter
    # upload file-a with todelete=true
    * header Authorization = auth
    Given path bucket
    And multipart file file = { read: 'RESTHeart.pdf', filename: 'a.pdf' }
    And multipart field metadata = '{"_id": "file-a", "filename": "a.pdf", "todelete": true}'
    When method POST
    Then status 201

    # upload file-b with todelete=true
    * header Authorization = auth
    Given path bucket
    And multipart file file = { read: 'RESTHeart.pdf', filename: 'b.pdf' }
    And multipart field metadata = '{"_id": "file-b", "filename": "b.pdf", "todelete": true}'
    When method POST
    Then status 201

    # upload file-c without todelete flag (must survive)
    * header Authorization = auth
    Given path bucket
    And multipart file file = { read: 'RESTHeart.pdf', filename: 'c.pdf' }
    And multipart field metadata = '{"_id": "file-c", "filename": "c.pdf"}'
    When method POST
    Then status 201

    # bulk delete only files where metadata.todelete == true
    * header Authorization = auth
    Given path bucket + '/*'
    And param filter = '{"metadata.todelete": true}'
    When method DELETE
    Then status 200
    And match response.deleted == 2

    # file-c must still exist
    * header Authorization = auth
    Given path bucket + '/file-c'
    When method GET
    Then status 200
    And match response._id == 'file-c'

    # file-a must be gone
    * header Authorization = auth
    Given path bucket + '/file-a'
    When method GET
    Then status 404

    # file-b must be gone
    * header Authorization = auth
    Given path bucket + '/file-b'
    When method GET
    Then status 404

Scenario: Bulk delete all remaining files (no filter)
    # file-c was left from previous scenario; delete everything
    * header Authorization = auth
    Given path bucket + '/*'
    When method DELETE
    Then status 200
    And match response.deleted == 1

    # bucket must now be empty
    * header Authorization = auth
    Given path bucket
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[0]'

Scenario: Bulk delete on empty bucket returns deleted 0
    * header Authorization = auth
    Given path bucket + '/*'
    When method DELETE
    Then status 200
    And match response.deleted == 0

Scenario: Cleanup - delete test db
    * header Authorization = auth
    Given path db
    When method GET
    Then status 200
    * def dbEtag = responseHeaders['ETag'][0]

    * header Authorization = auth
    * header If-Match = dbEtag
    Given path db
    When method DELETE
    Then status 204
