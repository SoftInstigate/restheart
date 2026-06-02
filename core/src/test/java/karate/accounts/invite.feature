Feature: POST /auth/invite

  # Tests for the team-invitation endpoint.
  # Uses ownerAuth (owner-test@example.com) which is created/activated once per run.
  # X-Skip-Email: true is set globally in karate-config.js.

  Background:
    * url baseUrl
    * configure followRedirects = false
    # Ensure owner-test@example.com exists and is active (idempotent)
    * callonce read('classpath:karate/accounts/helpers/setup-owner.feature')

  # ---------------------------------------------------------------------------
  Scenario: happy path — owner invites a new user returns 201
  # ---------------------------------------------------------------------------
    * def inviteEmail = 'invite-user-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = ownerAuth
    And request { "email": "#(inviteEmail)", "role": "user" }
    When method POST
    Then status 201

  # ---------------------------------------------------------------------------
  Scenario: owner invites with role=admin returns 201
  # ---------------------------------------------------------------------------
    * def inviteEmail = 'invite-admin-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = ownerAuth
    And request { "email": "#(inviteEmail)", "role": "admin" }
    When method POST
    Then status 201

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request — returns 401
  # ---------------------------------------------------------------------------
    * def inviteEmail = 'invite-unauth-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And request { "email": "#(inviteEmail)", "role": "user" }
    When method POST
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: authenticated user without owner/admin role — returns 403
  # ---------------------------------------------------------------------------
    # 1. Invite a plain user (role: user) using the owner
    * def regularEmail = 'regular-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/invite'
    And header Authorization = ownerAuth
    And request { "email": "#(regularEmail)", "role": "user" }
    When method POST
    Then status 201

    # 2. Get invite token from MongoDB
    Given path '/users/' + regularEmail
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def inviteToken = response.inviteToken

    # 3. Activate the invited user (sets password, makes them active with role 'user')
    Given path '/auth/activate'
    And request
      """
      {
        "email":    "#(regularEmail)",
        "token":    "#(inviteToken)",
        "password": "Password123!",
        "consents": { "terms": true, "privacy": true }
      }
      """
    When method PATCH
    Then status 200

    # 4. Compute Basic auth for the regular user (role: user, not owner)
    * def JavaString = Java.type('java.lang.String')
    * def Base64     = Java.type('java.util.Base64')
    * def regularAuth = 'Basic ' + Base64.getEncoder().encodeToString(new JavaString(regularEmail + ':Password123!').getBytes('UTF-8'))

    # 5. Attempt to invite with the regular (user-role) account — must be 403
    * def inviteTarget = 'invite-403-target-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/invite'
    And header Authorization = regularAuth
    And request { "email": "#(inviteTarget)", "role": "user" }
    When method POST
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: duplicate invite — inviting the same email twice returns 409
  # ---------------------------------------------------------------------------
    * def inviteEmail = 'invite-dup-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = ownerAuth
    And request { "email": "#(inviteEmail)", "role": "user" }
    When method POST
    Then status 201

    Given path '/auth/invite'
    And header Authorization = ownerAuth
    And request { "email": "#(inviteEmail)", "role": "user" }
    When method POST
    Then status 409

  # ---------------------------------------------------------------------------
  Scenario: missing email in body — returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/invite'
    And header Authorization = ownerAuth
    And request { "role": "user" }
    When method POST
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: verify DB — after invite user has status=invited and inviteToken set
  # ---------------------------------------------------------------------------
    * def inviteEmail = 'invite-db-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = ownerAuth
    And request { "email": "#(inviteEmail)", "role": "user" }
    When method POST
    Then status 201

    Given path '/users/' + inviteEmail
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.status == 'invited'
    And match response.inviteToken == '#notnull'
    And match response.inviteCreatedAt == '#notnull'
