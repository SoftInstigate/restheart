# Tests that the collection's JSON Schema is applied on restheart-accounts' user
# UPDATE paths (#658) — DbHelper.updateUser ($set) and DbHelper.unsetUserFields
# ($unset), both of which write directly via the MongoDB driver and therefore
# bypass restheart-mongodb's own JsonSchemaBeforeWriteChecker.
#
# Scenarios deliberately go through real restheart-accounts endpoints
# (/auth/reset-password, /auth/profile) rather than the generic Mongo REST
# PATCH /restheart-test/users/{email}: the latter is checked by
# JsonSchemaBeforeWriteChecker in restheart-mongodb, which is unrelated to the
# accounts-side validation this issue adds and would pass even if DbHelper
# never validated anything.
#
# Karate has no teardown, so each scenario clears the 'jsonSchema' metadata on
# its way out AND the Background clears it on the way in.
#
# X-Skip-Email: true is set globally in karate-config.js.

@accounts @schema
Feature: Apply JSON Schema on user update

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def adminAuth = adminAuth
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

    # --- register + verify a fresh user, then request a password reset ---
    # (exercises both DbHelper.updateUser and DbHelper.unsetUserFields via
    # PATCH /auth/reset-password: it $sets 'password' then $unsets the
    # 'passwordResetToken'/'passwordResetCreatedAt' pair)
    * def email = 'upd-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request { "firstName": "Update", "lastName": "Test", "teamName": "UT Corp", "email": "#(email)", "password": "OldPassword1!" }
    When method POST
    Then status 201

    Given path usersColl + '/' + email
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def verifyToken = response.emailVerificationToken

    Given path '/auth/verify'
    And param email = email
    And param token = verifyToken
    When method GET
    Then status 302
    * def jwt = responseHeaders['Set-Cookie'][0].split('Bearer_')[1].split(';')[0]

    Given path '/auth/forgot-password'
    And request { "email": "#(email)" }
    When method POST
    Then status 202

    Given path usersColl + '/' + email
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def resetToken = response.passwordResetToken

  # ---------------------------------------------------------------------------
  Scenario: no jsonSchema metadata means no validation on update or unset
  # ---------------------------------------------------------------------------
    Given path '/auth/reset-password'
    And request { "email": "#(email)", "token": "#(resetToken)", "password": "NewPassword1!" }
    When method PATCH
    Then status 200

    # the one-shot token fields were unset
    Given path usersColl + '/' + email
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.passwordResetToken == '#notpresent'

  # ---------------------------------------------------------------------------
  Scenario: unsetUserFields validates its own candidate — required field removed by $unset fails closed
  # ---------------------------------------------------------------------------
    # a field only ever removed by unsetUserFields, never touched by the $set
    # step, so a schema requiring it exercises the $unset candidate independently
    * header Authorization = adminAuth
    Given path schemas
    And request { "_id": "user-needs-reset-token", "$schema": "http://json-schema.org/draft-07/schema#", "type": "object", "required": ["passwordResetToken"] }
    When method POST
    Then assert responseStatus == 201 || responseStatus == 409

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "user-needs-reset-token" } }
    When method PATCH
    Then assert responseStatus == 200

    Given path '/auth/reset-password'
    And request { "email": "#(email)", "token": "#(resetToken)", "password": "NewPassword1!" }
    When method PATCH
    Then status 400
    And match response.message contains 'violates schema'

    # the $unset was rejected — the token field must still be there, unlike the
    # no-schema scenario above
    Given path usersColl + '/' + email
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.passwordResetToken == '#(resetToken)'

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200

  # ---------------------------------------------------------------------------
  Scenario: update validation fails closed when the declared schema does not exist
  # ---------------------------------------------------------------------------
    # a declared but unresolvable schema must not silently degrade to "no
    # validation" — that would defeat the point of the feature
    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": { "schemaId": "no-such-schema-anywhere" } }
    When method PATCH
    Then assert responseStatus == 200

    Given path '/auth/reset-password'
    And request { "email": "#(email)", "token": "#(resetToken)", "password": "NewPassword1!" }
    When method PATCH
    Then status 500

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200

  # ---------------------------------------------------------------------------
  Scenario: a satisfied schema lets the update through — dotted paths merge, unrelated fields survive
  # ---------------------------------------------------------------------------
    # the schema speaks the vocabulary of the stored document, like
    # register-schema-validation.feature's 'user-satisfiable' schema
    * header Authorization = adminAuth
    Given path schemas
    And request
      """
      {
        "_id": "user-satisfiable-upd",
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
    And request { "jsonSchema": { "schemaId": "user-satisfiable-upd" } }
    When method PATCH
    Then assert responseStatus == 200

    # only 'profile.name' (firstName) is sent; 'profile.surname' — set at
    # registration — must survive the dotted-path merge, or the candidate
    # document built by DbHelper would spuriously fail 'required'
    Given path '/auth/profile'
    And header Authorization = 'Bearer ' + jwt
    And request { "firstName": "Updated" }
    When method PATCH
    Then status 200

    Given path usersColl + '/' + email
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.profile.name == 'Updated'
    And match response.profile.surname == 'Test'

    * header Authorization = adminAuth
    Given path usersColl
    And request { "jsonSchema": null }
    When method PATCH
    Then assert responseStatus == 200
