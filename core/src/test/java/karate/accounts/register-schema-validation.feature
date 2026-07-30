# Tests that the collection's JSON Schema is applied on user registration (#657).

@accounts @schema
Feature: Apply JSON Schema on user registration

  Background:
    * url baseUrl
    * def adminAuth = adminAuth

  # ---------------------------------------------------------------------------
  Scenario: registration fails when collection schema requires missing field
  # ---------------------------------------------------------------------------
    # Create schema store in the accounts test database
    * header Authorization = adminAuth
    Given path '/restheart-test/_schemas'
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # Create a schema that requires a 'consents' field
    * header Authorization = adminAuth
    Given path '/restheart-test/_schemas'
    And request { "_id": "user-with-consents", "$schema": "http://json-schema.org/draft-07/schema#", "type": "object", "required": ["consents"], "properties": { "consents": { "type": "object" } } }
    When method POST
    Then assert responseStatus == 201

    # Apply schema to users collection
    * header Authorization = adminAuth
    Given path '/restheart-test/users'
    And request { "jsonSchema": { "schemaId": "user-with-consents" } }
    When method PATCH
    Then assert responseStatus == 200

    # Try to register — should fail because user doc has no 'consents'
    * def email = 'reg-schema-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Schema",
        "lastName":  "Test",
        "teamName":  "ST Corp",
        "email":     "#(email)",
        "password":  "Password123!"
      }
      """
    When method POST
    Then status 400

    # Cleanup: remove schema from users collection
    * header Authorization = adminAuth
    Given path '/restheart-test/users'
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200

    # Now registration should succeed
    * def email2 = 'reg-schema-ok-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Schema",
        "lastName":  "Test",
        "teamName":  "ST Corp",
        "email":     "#(email2)",
        "password":  "Password123!"
      }
      """
    When method POST
    Then status 201
