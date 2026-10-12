# Tests that the invitation endpoints work when the users collection declares a
# JSON Schema. InviteService, ResendInviteService and AcceptInviteService write
# user documents through DbHelper, which validates them against the schema; they
# did not receive the 'json-schemas' provider, so every invitation on a users
# collection with a schema failed with 500 "the JSON Schema store is not available".
#
# Karate has no teardown, so each scenario clears the 'jsonSchema' metadata on
# its way out AND the Background clears it on the way in.
#
# X-Skip-Email: true is set globally in karate-config.js.

@accounts @schema
Feature: Invitations on a users collection with a JSON Schema

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def usersColl = '/restheart-test/users'
    * def schemas = '/restheart-test/_schemas'
    * def secondSetup = karate.call('classpath:karate/accounts/helpers/setup-second-team.feature')
    * def secondOwnerJwt = secondSetup.secondOwnerJwt

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

    # a schema every user document satisfies: the point is that validation runs, not that it refuses
    * header Authorization = adminAuth
    Given path schemas
    And request { "_id": "user-invite-any", "$schema": "http://json-schema.org/draft-07/schema#", "type": "object" }
    When method POST
    Then assert responseStatus == 201 || responseStatus == 409

  # ---------------------------------------------------------------------------
  Scenario: inviting a new user writes the invited account through the schema
  # ---------------------------------------------------------------------------
    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "user-invite-any" } }
    When method PATCH
    Then assert responseStatus == 200

    * def newUserEmail = 'invite-schema-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + secondOwnerJwt
    And request { "email": "#(newUserEmail)", "role": "member" }
    When method POST
    Then status 201

    Given path usersColl + '/' + newUserEmail
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.roles contains '$unauthenticated'

    # resending rewrites the invitation on the same document
    Given path '/auth/resend-invite'
    And header Authorization = 'Bearer ' + secondOwnerJwt
    And request { "email": "#(newUserEmail)" }
    When method POST
    Then assert responseStatus < 500

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200

  # ---------------------------------------------------------------------------
  Scenario: inviting an existing user updates the account through the schema
  # ---------------------------------------------------------------------------
    # an existing, verified user, registered before the schema is declared
    * def existingEmail = 'invite-schema-existing-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/register'
    And request { "firstName": "Invited", "lastName": "Existing", "teamName": "Own Team", "email": "#(existingEmail)", "password": "Password-123!" }
    When method POST
    Then status 201

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "user-invite-any" } }
    When method PATCH
    Then assert responseStatus == 200

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + secondOwnerJwt
    And request { "email": "#(existingEmail)", "role": "member" }
    When method POST
    Then status 201

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200
