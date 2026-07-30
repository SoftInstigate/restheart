# Tests that the collection's JSON Schema is applied on user registration (#657).
#
# These scenarios put 'jsonSchema' metadata on restheart-test/users, which every other
# @accounts feature writes to. Karate has no teardown, so each scenario clears the
# metadata on its way out AND the Background clears it on the way in: a scenario that
# fails halfway cannot leave the collection validated for the next one.
#
# X-Skip-Email: true is set globally in karate-config.js.

@accounts @schema
Feature: Apply JSON Schema on user registration

  Background:
    * url baseUrl
    * def usersColl = '/restheart-test/users'
    * def schemas = '/restheart-test/_schemas'

    # the schema store must exist before a schema can be declared
    * header Authorization = adminAuth
    Given path schemas
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # start from a users collection with no schema, whatever the previous scenario left
    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200

  # ---------------------------------------------------------------------------
  Scenario: no jsonSchema metadata means no validation
  # ---------------------------------------------------------------------------
    * def email = 'reg-noschema-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request { "firstName": "Schema", "lastName": "Test", "teamName": "ST Corp", "email": "#(email)", "password": "Password123!" }
    When method POST
    Then status 201

  # ---------------------------------------------------------------------------
  Scenario: a satisfied schema lets registration through
  # ---------------------------------------------------------------------------
    # the schema speaks the vocabulary of the stored document: firstName maps to
    # profile.name and lastName to profile.surname
    * header Authorization = adminAuth
    Given path schemas
    And request
      """
      {
        "_id": "user-satisfiable",
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "required": ["_id", "password", "roles", "profile"],
        "properties": {
          "profile": {
            "type": "object",
            "required": ["name", "surname"],
            "properties": {
              "name":    { "type": "string" },
              "surname": { "type": "string" }
            }
          }
        }
      }
      """
    When method POST
    Then assert responseStatus == 201 || responseStatus == 409

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "user-satisfiable" } }
    When method PATCH
    Then assert responseStatus == 200

    * def email = 'reg-schema-ok-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request { "firstName": "Schema", "lastName": "Test", "teamName": "ST Corp", "email": "#(email)", "password": "Password123!" }
    When method POST
    Then status 201

    # the document that landed is the one the schema described
    * header Authorization = adminAuth
    Given path usersColl + '/' + email
    When method GET
    Then status 200
    And match response.profile.name == 'Schema'
    And match response.profile.surname == 'Test'

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200

  # ---------------------------------------------------------------------------
  Scenario: registration fails with 400 when the schema requires a missing field
  # ---------------------------------------------------------------------------
    # 'consents' is never produced by /auth/register, so the user document can
    # never satisfy this schema
    * header Authorization = adminAuth
    Given path schemas
    And request { "_id": "user-with-consents", "$schema": "http://json-schema.org/draft-07/schema#", "type": "object", "required": ["consents"], "properties": { "consents": { "type": "object" } } }
    When method POST
    Then assert responseStatus == 201 || responseStatus == 409

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "user-with-consents" } }
    When method PATCH
    Then assert responseStatus == 200

    * def email = 'reg-schema-ko-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request { "firstName": "Schema", "lastName": "Test", "teamName": "ST Corp", "email": "#(email)", "password": "Password123!" }
    When method POST
    Then status 400
    And match response.message contains 'violates schema'

    # the user must not have been created
    * header Authorization = adminAuth
    Given path usersColl + '/' + email
    When method GET
    Then status 404

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200

  # ---------------------------------------------------------------------------
  Scenario: validation fails closed when the declared schema does not exist
  # ---------------------------------------------------------------------------
    # a declared but unresolvable schema must not silently degrade to "no
    # validation" — that would defeat the point of the feature
    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "no-such-schema-anywhere" } }
    When method PATCH
    Then assert responseStatus == 200

    * def email = 'reg-schema-missing-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request { "firstName": "Schema", "lastName": "Test", "teamName": "ST Corp", "email": "#(email)", "password": "Password123!" }
    When method POST
    Then status 500

    # the user must not have been created
    * header Authorization = adminAuth
    Given path usersColl + '/' + email
    When method GET
    Then status 404

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200

  # ---------------------------------------------------------------------------
  Scenario: malformed jsonSchema metadata fails closed too
  # ---------------------------------------------------------------------------
    # 'schemaId' missing from the metadata
    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaStoreDb": "restheart-test" } }
    When method PATCH
    Then assert responseStatus == 200

    * def email = 'reg-schema-malformed-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request { "firstName": "Schema", "lastName": "Test", "teamName": "ST Corp", "email": "#(email)", "password": "Password123!" }
    When method POST
    Then status 500

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200
