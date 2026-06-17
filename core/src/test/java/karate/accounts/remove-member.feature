Feature: DELETE /auth/remove-member

  # Tests for the member removal endpoint.
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
  Scenario: happy path — owner removes a member returns 200
  # ---------------------------------------------------------------------------
    * def memberEmail = 'rm-happy-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "Remove1!" }
    When method PATCH
    Then status 200

    Given path '/auth/remove-member'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)" }
    When method DELETE
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/remove-member'
    And request { "email": "anyone@example.com" }
    When method DELETE
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: regular member (non-owner/admin) cannot remove — returns 403
  # ---------------------------------------------------------------------------
    * def memberEmail = 'rm-403-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "Remove1!" }
    When method PATCH
    Then status 200
    * def setCookie = responseHeaders['Set-Cookie'][0]
    * def memberJwt = setCookie.split('Bearer_')[1].split(';')[0]

    Given path '/auth/remove-member'
    And header Authorization = 'Bearer ' + memberJwt
    And request { "email": "someone@example.com" }
    When method DELETE
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: target not a member of the tenant — returns 404
  # ---------------------------------------------------------------------------
    * def unknownEmail = 'notamember-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/auth/remove-member'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(unknownEmail)" }
    When method DELETE
    Then status 404

  # ---------------------------------------------------------------------------
  Scenario: missing email in body — returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/remove-member'
    And header Authorization = 'Bearer ' + ownerJwt
    And request {}
    When method DELETE
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: owner cannot remove themselves — returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/remove-member'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "owner-test@example.com" }
    When method DELETE
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: admin can remove a member
  # ---------------------------------------------------------------------------
    * def adminEmail = 'rm-admin-caller-' + java.util.UUID.randomUUID() + '@example.com'
    * def memberEmail = 'rm-by-admin-' + java.util.UUID.randomUUID() + '@example.com'

    # Invite and activate admin
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(adminEmail)", "role": "owner" }
    When method POST
    Then status 201

    * def adminTokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: adminEmail })
    * def adminInviteToken = adminTokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(adminEmail)", "token": "#(adminInviteToken)", "password": "AdminPass1!" }
    When method PATCH
    Then status 200
    * def adminJwt = responseHeaders['Set-Cookie'][0].split('Bearer_')[1].split(';')[0]

    # Invite and activate a regular member
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def memberTokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def memberInviteToken = memberTokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(memberInviteToken)", "password": "MemberPass1!" }
    When method PATCH
    Then status 200

    # Admin removes the member
    Given path '/auth/remove-member'
    And header Authorization = 'Bearer ' + adminJwt
    And request { "email": "#(memberEmail)" }
    When method DELETE
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: verify DB — after removal user's tenants array no longer contains the tenant
  # ---------------------------------------------------------------------------
    * def memberEmail = 'rm-db-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "Remove1!" }
    When method PATCH
    Then status 200

    # Read the tenant ID before removal
    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.tenants == '#array'
    * assert response.tenants.length >= 1

    Given path '/auth/remove-member'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)" }
    When method DELETE
    Then status 200

    # Verify tenants array is now empty
    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.tenants == '#[0]'
    And match response.tenant == '#notpresent'
