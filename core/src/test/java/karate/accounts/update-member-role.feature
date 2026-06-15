Feature: PATCH /auth/member-role

  # Tests for the member role update endpoint.
  # X-Skip-Email: true is set globally in karate-config.js.
  #
  # Background (per scenario): sets up owner-test@example.com.
  # Each scenario invites + activates a fresh member so tests are fully isolated.

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def setupResult = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = setupResult.ownerJwt

  # ---------------------------------------------------------------------------
  Scenario: happy path — owner promotes member to owner returns 200
  # ---------------------------------------------------------------------------
    * def memberEmail = 'umr-promote-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def inviteToken = response.inviteToken

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "Promote1!" }
    When method PATCH
    Then status 200

    Given path '/auth/member-role'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "owner" }
    When method PATCH
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: happy path — owner demotes owner back to member returns 200
  # ---------------------------------------------------------------------------
    * def memberEmail = 'umr-demote-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "owner" }
    When method POST
    Then status 201

    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def inviteToken = response.inviteToken

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "Demote1!" }
    When method PATCH
    Then status 200

    Given path '/auth/member-role'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method PATCH
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/member-role'
    And request { "email": "anyone@example.com", "role": "owner" }
    When method PATCH
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: regular member cannot update roles — returns 403
  # ---------------------------------------------------------------------------
    * def memberEmail = 'umr-403-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def inviteToken = response.inviteToken

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "Forbid1!" }
    When method PATCH
    Then status 200
    * def memberJwt = responseHeaders['Set-Cookie'][0].split('Bearer_')[1].split(';')[0]

    Given path '/auth/member-role'
    And header Authorization = 'Bearer ' + memberJwt
    And request { "email": "someone@example.com", "role": "owner" }
    When method PATCH
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: invalid role value — returns 400
  # ---------------------------------------------------------------------------
    * def memberEmail = 'umr-badrole-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def inviteToken = response.inviteToken

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "BadRole1!" }
    When method PATCH
    Then status 200

    # "superadmin" is not a valid role for this endpoint
    Given path '/auth/member-role'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "superadmin" }
    When method PATCH
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: missing role field — returns 400
  # ---------------------------------------------------------------------------
    * def memberEmail = 'umr-missingrole-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def inviteToken = response.inviteToken

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "Missing1!" }
    When method PATCH
    Then status 200

    Given path '/auth/member-role'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)" }
    When method PATCH
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: target not a member of the tenant — returns 404
  # ---------------------------------------------------------------------------
    Given path '/auth/member-role'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "notamember@example.com", "role": "owner" }
    When method PATCH
    Then status 404

  # ---------------------------------------------------------------------------
  Scenario: owner caller can update another member's role
  # ---------------------------------------------------------------------------
    * def ownerCallerEmail = 'umr-owner-caller-' + java.util.UUID.randomUUID() + '@example.com'
    * def memberEmail = 'umr-by-owner-' + java.util.UUID.randomUUID() + '@example.com'

    # Invite and activate owner caller
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(ownerCallerEmail)", "role": "owner" }
    When method POST
    Then status 201

    Given path '/users/' + ownerCallerEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def ownerInviteToken = response.inviteToken

    Given path '/auth/activate'
    And request { "email": "#(ownerCallerEmail)", "token": "#(ownerInviteToken)", "password": "OwnerCaller1!" }
    When method PATCH
    Then status 200
    * def ownerCallerJwt = responseHeaders['Set-Cookie'][0].split('Bearer_')[1].split(';')[0]

    # Invite and activate a regular member
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def memberInviteToken = response.inviteToken

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(memberInviteToken)", "password": "MemberPass1!" }
    When method PATCH
    Then status 200

    # Owner promotes member to admin
    Given path '/auth/member-role'
    And header Authorization = 'Bearer ' + ownerCallerJwt
    And request { "email": "#(memberEmail)", "role": "owner" }
    When method PATCH
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: verify DB — role updated in user.tenants[] after PATCH
  # ---------------------------------------------------------------------------
    * def memberEmail = 'umr-db-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def inviteToken = response.inviteToken

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "DbCheck1!" }
    When method PATCH
    Then status 200

    # Verify initial role
    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def initialRole = response.tenants[0].role
    * match initialRole == 'member'

    # Promote to admin
    Given path '/auth/member-role'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "owner" }
    When method PATCH
    Then status 200

    # Verify updated role in DB
    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def updatedRole = response.tenants[0].role
    * match updatedRole == 'owner'
