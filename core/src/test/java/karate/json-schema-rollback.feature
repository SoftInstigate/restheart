@schema
Feature: A write refused by jsonSchemaAfterWrite leaves no trace

# A PATCH carrying update operators has no document to validate until it has been applied, so it is
# checked at RESPONSE and undone. Where MongoDB is a replica set the write runs in a transaction and
# the undo is its abort, which is also what makes a bulk PATCH checkable at all: it used to be
# refused with 501, because there was no way to undo one.

Background:
* url 'http://localhost:8080'
* def db = '/test-json-schema-rollback'
* def coll = db + '/coll'
* def schemas = db + '/_schemas'
* def authHeader = 'Basic YWRtaW46c2VjcmV0'

Scenario: Set up a collection validated by a schema

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
    And request { "_id": "person", "$schema": "https://json-schema.org/draft-07/schema#", "type": "object", "properties": { "name": { "type": "string" }, "age": { "type": "number", "minimum": 18 } }, "required": ["name", "age"] }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path coll
    And request { "jsonSchema": { "schemaId": "person" } }
    When method PUT
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path coll
    And request { "_id": "ann", "name": "Ann", "age": 30 }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path coll
    And request { "_id": "bob", "name": "Bob", "age": 40 }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path coll
    And request { "_id": "cal", "name": "Cal", "age": 50 }
    When method POST
    Then assert responseStatus == 201

# ------------------------------------------------------------------------------------------------
# one document
# ------------------------------------------------------------------------------------------------

Scenario: A refused PATCH leaves the document exactly as it was, etag included

    * header Authorization = authHeader
    Given path coll + '/ann'
    When method GET
    Then assert responseStatus == 200
    * def before = response

    # $set is an update operator, so the outcome is only knowable after the write
    * header Authorization = authHeader
    Given path coll + '/ann'
    And request { "$set": { "age": 12 } }
    When method PATCH
    Then assert responseStatus == 400
    And match response.message contains 'is not greater or equal to 18'

    # the etag matters as much as the value: a compensating write would have produced a new one
    * header Authorization = authHeader
    Given path coll + '/ann'
    When method GET
    Then assert responseStatus == 200
    And match response == before

Scenario: A PATCH that satisfies the schema is applied

    * header Authorization = authHeader
    Given path coll + '/ann'
    And request { "$set": { "age": 31 } }
    When method PATCH
    Then assert responseStatus == 200

    * header Authorization = authHeader
    Given path coll + '/ann'
    When method GET
    Then assert responseStatus == 200
    And match response.age == 31

# ------------------------------------------------------------------------------------------------
# bulk: only checkable because the write runs in a transaction
# ------------------------------------------------------------------------------------------------

Scenario: A bulk PATCH that violates the schema is refused, and touches nothing

    * header Authorization = authHeader
    Given path coll + '/bob'
    When method GET
    * def bobBefore = response

    * header Authorization = authHeader
    Given path coll + '/cal'
    When method GET
    * def calBefore = response

    * header Authorization = authHeader
    Given path coll + '/*'
    And param filter = '{"age":{"$gte":40}}'
    And request { "$set": { "age": 5 } }
    When method PATCH
    Then assert responseStatus == 400
    And match response.message contains 'is not greater or equal to 18'

    # both matched documents, not just the first one that failed
    * header Authorization = authHeader
    Given path coll + '/bob'
    When method GET
    Then match response == bobBefore

    * header Authorization = authHeader
    Given path coll + '/cal'
    When method GET
    Then match response == calBefore

Scenario: A bulk PATCH cannot escape the check by moving a document out of its own filter

    # "age": "young" both violates the schema and stops matching {"age":{"$gte":40}}, so re-reading
    # by the filter after the write would find nothing to validate. The ids are captured instead.
    * header Authorization = authHeader
    Given path coll + '/bob'
    When method GET
    * def bobBefore = response

    * header Authorization = authHeader
    Given path coll + '/*'
    And param filter = '{"age":{"$gte":40}}'
    And request { "$set": { "age": "young" } }
    When method PATCH
    Then assert responseStatus == 400

    * header Authorization = authHeader
    Given path coll + '/bob'
    When method GET
    Then match response == bobBefore

Scenario: A bulk PATCH that satisfies the schema is applied to every matched document

    * header Authorization = authHeader
    Given path coll + '/*'
    And param filter = '{"age":{"$gte":40}}'
    And request { "$set": { "age": 41 } }
    When method PATCH
    Then assert responseStatus == 200

    * header Authorization = authHeader
    Given path coll + '/bob'
    When method GET
    Then match response.age == 41

    * header Authorization = authHeader
    Given path coll + '/cal'
    When method GET
    Then match response.age == 41

Scenario: One invalid document takes back the whole bulk PATCH

    # $inc makes the outcome depend on each document, so the same update leaves some valid and one
    # not: from 30/40/50, -20 puts Ann at 10 and the others at 20. Nothing about Bob and Cal is
    # wrong, and neither of them may be written.
    * header Authorization = authHeader
    Given path coll + '/ann'
    And request { "$set": { "age": 30 } }
    When method PATCH
    Then assert responseStatus == 200

    * header Authorization = authHeader
    Given path coll + '/bob'
    And request { "$set": { "age": 40 } }
    When method PATCH
    Then assert responseStatus == 200

    * header Authorization = authHeader
    Given path coll + '/cal'
    And request { "$set": { "age": 50 } }
    When method PATCH
    Then assert responseStatus == 200

    * header Authorization = authHeader
    Given path coll + '/ann'
    When method GET
    * def annBefore = response

    * header Authorization = authHeader
    Given path coll + '/bob'
    When method GET
    * def bobBefore = response

    * header Authorization = authHeader
    Given path coll + '/cal'
    When method GET
    * def calBefore = response

    * header Authorization = authHeader
    Given path coll + '/*'
    And param filter = '{"_id":{"$exists":true}}'
    And request { "$inc": { "age": -20 } }
    When method PATCH
    Then assert responseStatus == 400
    And match response.message contains 'is not greater or equal to 18'

    * header Authorization = authHeader
    Given path coll + '/ann'
    When method GET
    Then match response == annBefore

    * header Authorization = authHeader
    Given path coll + '/bob'
    When method GET
    Then match response == bobBefore

    * header Authorization = authHeader
    Given path coll + '/cal'
    When method GET
    Then match response == calBefore
