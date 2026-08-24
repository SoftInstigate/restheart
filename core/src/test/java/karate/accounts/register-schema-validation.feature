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
    # OAuth fields (socialAuths, profile.avatarUrl) and team fields (teams, team)
    # are declared optional so that documents created via OAuth pass validation.
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
              "name":      { "type": "string" },
              "surname":   { "type": "string" },
              "avatarUrl": { "type": "string" }
            }
          },
          "socialAuths": { "type": "array" },
          "teams":       { "type": "array" },
          "team":        { "type": "object" }
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
  Scenario: registration fails with 400 when the body omits a required field
  # ---------------------------------------------------------------------------
    # With a schema, body properties pass through — but a field that is neither
    # in the body nor produced by the service still fails validation
    * header Authorization = adminAuth
    Given path schemas
    And request { "_id": "user-with-magic", "$schema": "http://json-schema.org/draft-07/schema#", "type": "object", "required": ["magicToken"], "properties": { "magicToken": { "type": "string" } } }
    When method POST
    Then assert responseStatus == 201 || responseStatus == 409

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "user-with-magic" } }
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
  Scenario: additional body properties pass through when a schema is configured
  # ---------------------------------------------------------------------------
    # The schema does not require 'consents' (the guard handles absence), but
    # validates its format when present.  OAuth fields are declared optional so
    # that documents created via OAuth or patched later pass validation.
    * header Authorization = adminAuth
    Given path schemas
    And request
      """
      {
        "_id": "user-with-consents",
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "required": ["_id", "password", "roles", "profile"],
        "properties": {
          "profile": {
            "type": "object",
            "required": ["name", "surname"],
            "properties": {
              "name":      { "type": "string" },
              "surname":   { "type": "string" },
              "avatarUrl": { "type": "string" }
            }
          },
          "consents": {
            "type": "object",
            "required": ["terms", "privacy"],
            "properties": {
              "terms":    { "type": "boolean" },
              "privacy":  { "type": "boolean" }
            }
          },
          "socialAuths": { "type": "array" },
          "teams":       { "type": "array" },
          "team":        { "type": "object" }
        }
      }
      """
    When method POST
    Then assert responseStatus == 201 || responseStatus == 409

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "user-with-consents" } }
    When method PATCH
    Then assert responseStatus == 200

    * def email = 'reg-schema-extra-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request { "firstName": "Schema", "lastName": "Test", "teamName": "ST Corp", "email": "#(email)", "password": "Password123!", "consents": { "terms": true, "privacy": true } }
    When method POST
    Then status 201

    # the stored document must contain the consents
    * header Authorization = adminAuth
    Given path usersColl + '/' + email
    When method GET
    Then status 200
    And match response.consents.terms == true
    And match response.consents.privacy == true

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200

  # ---------------------------------------------------------------------------
  Scenario: schema rejects consents with wrong format
  # ---------------------------------------------------------------------------
    # The schema validates consents format: must be an object with boolean fields.
    # A body carrying a string instead is rejected.
    * header Authorization = adminAuth
    Given path schemas
    And request
      """
      {
        "_id": "user-consents-format",
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "required": ["_id", "password", "roles", "profile"],
        "properties": {
          "profile": {
            "type": "object",
            "required": ["name", "surname"],
            "properties": {
              "name":      { "type": "string" },
              "surname":   { "type": "string" },
              "avatarUrl": { "type": "string" }
            }
          },
          "consents": {
            "type": "object",
            "required": ["terms", "privacy"],
            "properties": {
              "terms":    { "type": "boolean" },
              "privacy":  { "type": "boolean" }
            }
          },
          "socialAuths": { "type": "array" },
          "teams":       { "type": "array" },
          "team":        { "type": "object" }
        }
      }
      """
    When method POST
    Then assert responseStatus == 201 || responseStatus == 409

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "user-consents-format" } }
    When method PATCH
    Then assert responseStatus == 200

    * def email = 'reg-schema-badconsents-' + java.util.UUID.randomUUID() + '@example.com'

    # consents is a string, not the expected object → schema rejects it
    Given path '/auth/register'
    And request { "firstName": "Schema", "lastName": "Test", "teamName": "ST Corp", "email": "#(email)", "password": "Password123!", "consents": "yes" }
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
  Scenario: registration without consents succeeds when schema makes them optional
  # ---------------------------------------------------------------------------
    # consents are optional in the schema — absence is fine, the guard handles it
    * header Authorization = adminAuth
    Given path schemas
    And request
      """
      {
        "_id": "user-consents-optional",
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "required": ["_id", "password", "roles", "profile"],
        "properties": {
          "profile": {
            "type": "object",
            "required": ["name", "surname"],
            "properties": {
              "name":      { "type": "string" },
              "surname":   { "type": "string" },
              "avatarUrl": { "type": "string" }
            }
          },
          "consents": {
            "type": "object",
            "required": ["terms", "privacy"],
            "properties": {
              "terms":    { "type": "boolean" },
              "privacy":  { "type": "boolean" }
            }
          },
          "socialAuths": { "type": "array" },
          "teams":       { "type": "array" },
          "team":        { "type": "object" }
        }
      }
      """
    When method POST
    Then assert responseStatus == 201 || responseStatus == 409

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "user-consents-optional" } }
    When method PATCH
    Then assert responseStatus == 200

    * def email = 'reg-noconsents-' + java.util.UUID.randomUUID() + '@example.com'

    # body omits consents — schema is fine, guard handles the absence later
    Given path '/auth/register'
    And request { "firstName": "Schema", "lastName": "Test", "teamName": "ST Corp", "email": "#(email)", "password": "Password123!" }
    When method POST
    Then status 201

    # the stored document has no consents field
    * header Authorization = adminAuth
    Given path usersColl + '/' + email
    When method GET
    Then status 200
    And match response !contains { consents: '#notnull' }

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200

  # ---------------------------------------------------------------------------
  Scenario: mapped body fields are not carried over as additional properties
  # ---------------------------------------------------------------------------
    # firstName/lastName/email/teamName already reach the document through the
    # mapping table (profile.name, profile.surname, _id) or the team document, so
    # they must not also land as top-level properties. additionalProperties:false
    # is what makes this observable: the tenant declares the document it expects,
    # and a leaked mapped field fails the registration it never asked for.
    * header Authorization = adminAuth
    Given path schemas
    And request
      """
      {
        "_id": "user-no-extras",
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "additionalProperties": false,
        "required": ["_id", "password", "roles", "profile"],
        "properties": {
          "_id":      { "type": "string" },
          "_etag":    { "type": "object" },
          "password": { "type": "string" },
          "roles":    { "type": "array" },
          "profile": {
            "type": "object",
            "properties": {
              "name":    { "type": "string" },
              "surname": { "type": "string" }
            }
          },
          "consents": { "type": "object" },
          "emailVerificationToken":     { "type": "string" },
          "emailVerificationCreatedAt": { "type": "object" }
        }
      }
      """
    When method POST
    Then assert responseStatus == 201 || responseStatus == 409

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "user-no-extras" } }
    When method PATCH
    Then assert responseStatus == 200

    * def email = 'reg-no-extras-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request { "firstName": "Schema", "lastName": "Test", "teamName": "ST Corp", "email": "#(email)", "password": "Password123!", "consents": { "terms": true } }
    When method POST
    Then status 201

    * header Authorization = adminAuth
    Given path usersColl + '/' + email
    When method GET
    Then status 200
    # the mapped fields landed where the mapping table says, and nowhere else
    And match response.profile.name == 'Schema'
    And match response.profile.surname == 'Test'
    And match response.consents.terms == true
    And match response !contains { firstName: '#notnull' }
    And match response !contains { lastName: '#notnull' }
    And match response !contains { teamName: '#notnull' }
    And match response !contains { email: '#notnull' }

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200

  # ---------------------------------------------------------------------------
  Scenario: extra body properties are dropped when no schema is configured
  # ---------------------------------------------------------------------------
    # Without a schema the same body still drops 'consents'
    * def email = 'reg-noschema-extra-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request { "firstName": "Schema", "lastName": "Test", "teamName": "ST Corp", "email": "#(email)", "password": "Password123!", "consents": { "terms": true } }
    When method POST
    Then status 201

    * header Authorization = adminAuth
    Given path usersColl + '/' + email
    When method GET
    Then status 200
    And match response !contains { consents: '#notnull' }

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
