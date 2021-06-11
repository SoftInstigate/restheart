# note, more tests are defined on org.restheart.test.integration.JsonSchemaCheckerIT
Feature: Test json schema validation

Background:
* url 'http://localhost:8080'
* def db = '/test-json-schema'
* def coll = '/test-json-schema/coll'
* def schemas = '/test-json-schema/_schemas'
* def authHeader = 'Basic YWRtaW46c2VjcmV0'
* def mongoSchema = read('json-schema-mongo.json')
* def collSchema = read('json-schema-coll.json')

# CREATE TEST DATA

Scenario: Create test data
    * header Authorization = authHeader
    Given path db
    And request { }
    When method PUT
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path schemas
    And request { }
    When method PUT
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path schemas
    And request mongoSchema
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path schemas
    And request collSchema
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path coll
    And request { "jsonSchema": { "schemaId": "coll" } }
    When method PUT
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path coll
    And request { "n": 1, "s": "foo", "timestamp": { "$date": 1568295769260 } }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path coll
    And request { "_id": { "$oid": "609a4a069dacff2e6014d89f"}, "n": 1, "s": "foo", "timestamp": { "$date": 1568295769260 } }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path coll
    And request { "_id": "wrong", "n": 1, "s": "foo", "timestamp": { "$date": 1568295769260 } }
    When method POST
    Then assert responseStatus == 400

    * header Authorization = authHeader
    Given path coll
    And request { "n": "wrong", "s": "foo", "timestamp": { "$date": 1568295769260 } }
    When method POST
    Then assert responseStatus == 400

    * header Authorization = authHeader
    Given path coll
    And request { "n": 1, "s": 1000, "timestamp": { "$date": 1568295769260 } }
    When method POST
    Then assert responseStatus == 400

    * header Authorization = authHeader
    Given path coll
    And request { "n": 1, "s": "foo", "timestamp": 1568295769260 } }
    When method POST
    Then assert responseStatus == 400

    * header Authorization = authHeader
    Given path coll + "/609a4a069dacff2e6014d89f"
    And request { "n": "s" }
    When method PATCH
    Then assert responseStatus == 400

    * header Authorization = authHeader
    Given path coll + "/609a4a069dacff2e6014d89f"
    And request { "n": 2 }
    When method PATCH
    Then assert responseStatus == 200

    * header Authorization = authHeader
    Given path coll + "/609a4a069dacff2e6014d89f"
    And request { "timestamp": "1568295769260" }
    When method PATCH
    Then assert responseStatus == 400

    * header Authorization = authHeader
    Given path coll + "/609a4a069dacff2e6014d89f"
    And request { "timestamp": { "$date": 1568295769260 } }
    When method PATCH
    Then assert responseStatus == 200