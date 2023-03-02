Feature: Test PATCH with aggregation update

Background:
* url 'http://localhost:8080'
* def db = '/test-update-file-metadata'
* def bucket = '/test-update-file-metadata/bucket.files'
* def file = '/test-update-file-metadata/bucket.files/file'
* def authHeader = 'Basic YWRtaW46c2VjcmV0'

Scenario: Create test db and bucket
    * header Authorization = authHeader
    Given path db
    And request { }
    When method PUT
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path bucket
    And request { }
    When method PUT
    Then assert responseStatus == 201

Scenario: Create file with aggregation pipeline
    Given path bucket
    And header Authorization = authHeader
    And multipart file file = { read: 'RESTHeart.pdf', filename: 'RESTHeart.pdf' }
    And multipart field metadata = '{ "_id": "file", "foo": 1, "bar": true, "filename": "RESTHeart.pdf"}'
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path file
    When method GET
    Then assert responseStatus == 200
    And match response.metadata.foo == 1
    And match response.metadata.bar == true
    And match response.metadata.contentType == "application/pdf"
    And match response.filename == "RESTHeart.pdf"
    And match response.metadata.filename == "RESTHeart.pdf"

Scenario: Update metadata with PATCH
    * header Authorization = authHeader
    Given path file
    And request { "filename": "renamed.pdf", "foo": 1}
    When method PATCH
    Then assert responseStatus == 200

    * header Authorization = authHeader
    Given path file
    When method GET
    Then assert responseStatus == 200
    And match response._id == "file"
    And match response.filename == "renamed.pdf"
    And match response.length == '#number'
    And match response.chunkSize == '#number'
    And match response.uploadDate.$date == '#number'
    And match response.metadata.foo == 1
    And match response.metadata.bar == true
    And match response.metadata.contentType == "application/pdf"
    And match response.metadata.filename == "renamed.pdf"
    And match response.metadata.foo == 1

Scenario: Update metadata with PUT
    * header Authorization = authHeader
    Given path file
    And request { "filename": "renamed.pdf", "foo": 1}
    When method PUT
    Then assert responseStatus == 200

    * header Authorization = authHeader
    Given path file
    When method GET
    Then assert responseStatus == 200
    And match response._id == "file"
    And match response.filename == "renamed.pdf"
    And match response.length == '#number'
    And match response.chunkSize == '#number'
    And match response.uploadDate.$date == '#number'
    And match response.metadata == {"filename": "renamed.pdf", "foo": 1, "_etag": {"$oid": #string} }