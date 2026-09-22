# The schema store keeps the draft a schema declares, draft-07 when it declares none, and refuses
# the drafts it cannot validate (#750). It used to overwrite $schema with draft-04, and the
# keywords of later drafts were ignored without a word.

@schema-drafts
Feature: JSON Schema drafts in the schema store

  Background:
    * url baseUrl
    * def authHeader = adminAuth
    * def db = '/test-json-schema-drafts'
    * def schemas = db + '/_schemas'
    * def coll = db + '/coll'

  Scenario: setup
    * header Authorization = authHeader
    Given path db
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path schemas
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

  Scenario: a draft-07 schema keeps its draft, and its if/then and const are enforced
    * header Authorization = authHeader
    Given path schemas
    And param wm = 'upsert'
    And request { "_id": "d7", "$schema": "http://json-schema.org/draft-07/schema#", "type": "object", "properties": { "tier": { "const": "gold" } }, "if": { "properties": { "kind": { "const": "company" } }, "required": ["kind"] }, "then": { "required": ["vat"] } }
    When method POST
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path schemas, 'd7'
    When method GET
    Then status 200
    And match response['$schema'] == 'http://json-schema.org/draft-07/schema#'

    * header Authorization = authHeader
    Given path coll
    And request { "jsonSchema": { "schemaId": "d7" } }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path coll
    And request { "kind": "company" }
    When method POST
    Then status 400

    * header Authorization = authHeader
    Given path coll
    And request { "tier": "silver" }
    When method POST
    Then status 400

    * header Authorization = authHeader
    Given path coll
    And request { "kind": "company", "vat": "IT01", "tier": "gold" }
    When method POST
    Then status 201

  Scenario: a schema without $schema is draft-07
    * header Authorization = authHeader
    Given path schemas
    And param wm = 'upsert'
    And request { "_id": "nodraft", "type": "object" }
    When method POST
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path schemas, 'nodraft'
    When method GET
    Then status 200
    And match response['$schema'] == 'http://json-schema.org/draft-07/schema#'

  Scenario: a draft-04 schema stays draft-04
    * header Authorization = authHeader
    Given path schemas
    And param wm = 'upsert'
    And request { "_id": "d4", "$schema": "http://json-schema.org/draft-04/schema#", "type": "object", "properties": { "n": { "type": "number", "maximum": 10, "exclusiveMaximum": true } } }
    When method POST
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path schemas, 'd4'
    When method GET
    Then status 200
    And match response['$schema'] == 'http://json-schema.org/draft-04/schema#'

  Scenario: a draft the schema store cannot validate is refused
    * header Authorization = authHeader
    Given path schemas
    And request { "_id": "d2020", "$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object" }
    When method POST
    Then status 400
    And match response.message contains 'draft-04, draft-06 or draft-07'

  Scenario: a schema that breaks its draft's metaschema is refused
    * header Authorization = authHeader
    Given path schemas
    And request { "_id": "bad7", "$schema": "http://json-schema.org/draft-07/schema#", "type": "object", "properties": { "n": { "maximum": 10, "exclusiveMaximum": true } } }
    When method POST
    Then status 400
