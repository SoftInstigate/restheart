Feature: /users self-service write restriction (accountsInitializer veto)

  # accountsInitializer registers a veto that unconditionally restricts what the generic
  # MongoDB REST resource at /users can be used for, regardless of any ACL permission:
  #   - PUT/POST are always rejected.
  #   - PATCH is rejected only if it touches a denylisted field (password, roles, team,
  #     teams, sub, socialAuths, providerId, emailVerificationToken/CreatedAt,
  #     passwordResetToken/CreatedAt, inviteToken, _id) — dot notation and update
  #     operators like $set/$push count too. Every other field (profile.*, consents, and
  #     any app-level field the tenant adds) is left to the tenant's own ACL.
  #   - Roles listed in accountsConfig.users-unrestricted-roles bypass the restriction
  #     entirely — 'admin' is configured as exempt for this test env (conf-overrides.yml).
  #
  # The ACL rules seeded below grant broad access (no field restriction) to the caller's
  # own /users/{id} document, so any rejection observed here is caused by the veto, not by
  # the ACL itself.
  #
  # NOTE: the JWT's top-level `roles` claim is restheart-accounts's *system* ACL role,
  # which is "user" for every accounts-managed account regardless of team standing —
  # "owner"/"member" only exist in user.teams[].role, never in the JWT `roles` array. The
  # ACL rules below must therefore target role "user", not "member".

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def setupResult = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = setupResult.ownerJwt

    # ACL: role "user" can PUT/POST/PATCH its own /users/{id} document, no field
    # restriction — isolates the veto as the only thing that can reject these calls.
    Given path '/restheart-test/acl'
    And param wm = "upsert"
    And header Authorization = adminAuth
    And request
      """
      [
        {
          "_id": { "$oid": "000000000000000000000001" },
          "predicate": "path-template('/users/{userId}') and (method(PUT) or method(PATCH)) and (equals(@user._id, ${userId}) or equals(@user.sub, ${userId}))",
          "roles": ["user"],
          "priority": 1
        },
        {
          "_id": { "$oid": "000000000000000000000002" },
          "predicate": "path-template('/restheart-test/users/{userId}') and method(PATCH) and (equals(@user._id, ${userId}) or equals(@user.sub, ${userId}))",
          "roles": ["user"],
          "priority": 1
        },
        {
          "_id": { "$oid": "000000000000000000000003" },
          "predicate": "path('/users') and method(POST)",
          "roles": ["user"],
          "priority": 1
        }
      ]
      """
    When method POST
    Then status 200

    # Register + activate a fresh "member" via invite, get a Bearer JWT for it
    * def memberEmail = 'uwr-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "MemberPass1!" }
    When method PATCH
    Then status 200
    * def memberJwt = responseHeaders['Set-Cookie'][0].split('Bearer_')[1].split(';')[0]

  # ---------------------------------------------------------------------------
  Scenario: PATCH profile.* is allowed
  # ---------------------------------------------------------------------------
    Given path '/users/' + memberEmail
    And header Authorization = 'Bearer ' + memberJwt
    And request { "profile.name": "Updated Name" }
    When method PATCH
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: PATCH consents (a non-denylisted, non-profile field) is allowed
  # ---------------------------------------------------------------------------
    Given path '/users/' + memberEmail
    And header Authorization = 'Bearer ' + memberJwt
    And request { "consents": { "terms": { "accepted": true } } }
    When method PATCH
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: PATCH with dot notation on teams.*.role is rejected
  # ---------------------------------------------------------------------------
    Given path '/users/' + memberEmail
    And header Authorization = 'Bearer ' + memberJwt
    And request { "teams.0.role": "owner" }
    When method PATCH
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: PATCH with $set update operator on teams.*.role is rejected
  # ---------------------------------------------------------------------------
    Given path '/users/' + memberEmail
    And header Authorization = 'Bearer ' + memberJwt
    And request { "$set": { "teams.0.role": "owner" } }
    When method PATCH
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: PATCH with $push on teams is rejected
  # ---------------------------------------------------------------------------
    Given path '/users/' + memberEmail
    And header Authorization = 'Bearer ' + memberJwt
    And request { "$push": { "teams": { "id": { "$oid": "000000000000000000000099" }, "role": "member" } } }
    When method PATCH
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: PATCH with roles field is rejected
  # ---------------------------------------------------------------------------
    Given path '/users/' + memberEmail
    And header Authorization = 'Bearer ' + memberJwt
    And request { "roles": ["admin"] }
    When method PATCH
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: PUT is always rejected, even with only profile.* in the body
  # ---------------------------------------------------------------------------
    Given path '/users/' + memberEmail
    And header Authorization = 'Bearer ' + memberJwt
    And request { "profile": { "name": "Full Replace" } }
    When method PUT
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: POST /users is always rejected
  # ---------------------------------------------------------------------------
    * def newEmail = 'uwr-post-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/users'
    And header Authorization = 'Bearer ' + memberJwt
    And request { "_id": "#(newEmail)", "profile": { "name": "New" }, "roles": ["user"] }
    When method POST
    Then status 403

  # ---------------------------------------------------------------------------
  # Same restriction applies when the users collection is reached through a
  # different mongo-mounts alias (/restheart-test/users instead of /users) —
  # the veto matches the resolved collection, not the request path.
  Scenario: PATCH via an alternate mongo-mounts alias for the same collection is also rejected
  # ---------------------------------------------------------------------------
    Given path '/restheart-test/users/' + memberEmail
    And header Authorization = 'Bearer ' + memberJwt
    And request { "$set": { "teams.0.role": "owner" } }
    When method PATCH
    Then status 403

  # ---------------------------------------------------------------------------
  # 'admin' is configured as exempt in conf-overrides.yml — the same PATCH that
  # is rejected for "member" above succeeds here.
  Scenario: users-unrestricted-roles bypasses the restriction entirely
  # ---------------------------------------------------------------------------
    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And request { "$set": { "teams.0.role": "owner" } }
    When method PATCH
    Then status 200
