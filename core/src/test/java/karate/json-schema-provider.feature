# Tests that the json-schemas Provider is registered and functional (#656).
#
# Covers the MongoService pipeline after migrating the two checkers from
# JsonSchemaCacheSingleton.getInstance() to the Provider pattern. Consumption of the
# Provider from OUTSIDE the pipeline is covered by accounts/register-schema-validation.feature,
# where restheart-accounts validates with a raw MongoClient.

@schema
Feature: JSON Schema Provider (#656)

  Background:
    * url baseUrl
    * def authHeader = adminAuth

  # ---------------------------------------------------------------------------
  Scenario: provider is registered — schema validation still rejects invalid docs
  # ---------------------------------------------------------------------------
    # Setup: create db, schema store, schema, and collection with jsonSchema metadata
    * header Authorization = authHeader
    Given path '/test-json-schema-provider'
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path '/test-json-schema-provider/_schemas'
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path '/test-json-schema-provider/_schemas'
    And request { "_id": "test-schema", "$schema": "http://json-schema.org/draft-07/schema#", "type": "object", "properties": { "name": { "type": "string" }, "age": { "type": "number" } }, "required": ["name"] }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path '/test-json-schema-provider/coll'
    And request { "jsonSchema": { "schemaId": "test-schema" } }
    When method PUT
    Then assert responseStatus == 201

    # Valid document — should succeed
    * header Authorization = authHeader
    Given path '/test-json-schema-provider/coll'
    And request { "name": "Alice", "age": 30 }
    When method POST
    Then status 201

    # Invalid document (missing required 'name') — should be rejected with 400
    * header Authorization = authHeader
    Given path '/test-json-schema-provider/coll'
    And request { "age": 30 }
    When method POST
    Then status 400

    # Invalid document (wrong type for 'age') — should be rejected with 400
    * header Authorization = authHeader
    Given path '/test-json-schema-provider/coll'
    And request { "name": "Bob", "age": "not-a-number" }
    When method POST
    Then status 400

    # PATCH: insert a valid doc, then patch with wrong type
    * header Authorization = authHeader
    Given path '/test-json-schema-provider/coll'
    And request { "name": "Charlie", "age": 25 }
    When method POST
    Then status 201
    * def location = responseHeaders['Location'][0]
    * def docId = location.substring(location.lastIndexOf('/') + 1)

    # Valid PATCH — should succeed
    * header Authorization = authHeader
    Given path '/test-json-schema-provider/coll/' + docId
    And request { "age": 26 }
    When method PATCH
    Then status 200

    # Invalid PATCH (wrong type) — should be rejected
    * header Authorization = authHeader
    Given path '/test-json-schema-provider/coll/' + docId
    And request { "age": "old" }
    When method PATCH
    Then status 400

    # Bulk POST: the schema is resolved once for the whole array, and one invalid
    # document rejects the request
    * header Authorization = authHeader
    Given path '/test-json-schema-provider/coll'
    And request [ { "name": "Dave", "age": 40 }, { "name": "Erin", "age": 41 } ]
    When method POST
    Then status 200

    * header Authorization = authHeader
    Given path '/test-json-schema-provider/coll'
    And request [ { "name": "Frank", "age": 50 }, { "age": 51 } ]
    When method POST
    Then status 400

    # A declared but missing schema is an error, not a reason to skip validation
    * header Authorization = authHeader
    Given path '/test-json-schema-provider/coll'
    And request { "jsonSchema": { "schemaId": "no-such-schema-anywhere" } }
    When method PATCH
    Then assert responseStatus == 200

    * header Authorization = authHeader
    Given path '/test-json-schema-provider/coll'
    And request { "name": "Grace", "age": 60 }
    When method POST
    Then status 500

    # Cleanup: dropping the db takes its collections with it
    * header Authorization = authHeader
    Given path '/test-json-schema-provider'
    When method GET
    Then status 200
    * def etag = responseHeaders['ETag'][0]

    * headers { Authorization: '#(authHeader)', 'If-Match': '#(etag)' }
    Given path '/test-json-schema-provider'
    When method DELETE
    Then assert responseStatus == 204 || responseStatus == 200
