Feature: ACL gating by the active team role (@user.team.role)

  # End-to-end proof that the active team role, carried in user.team.role and
  # mirrored into the JWT `team` claim as { _id, role }, is usable from an ACL
  # permission to gate an operation (predicate equals[@user.team.role,"owner"]).
  #
  # The permission lives in conf-overrides.yml (role: user, path-prefix
  # /restheart-test/team-partitioned). Every verified accounts user has system role
  # `user`; the predicate refines it to active-team owners only.

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def setupResult = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = setupResult.ownerJwt
    # ensure the partitioned collection exists (idempotent across runs)
    Given path '/restheart-test/team-partitioned'
    And header Authorization = adminAuth
    When method PUT
    * match [200, 201, 409] contains responseStatus

  # ---------------------------------------------------------------------------
  Scenario: active-team owner may POST (predicate @user.team.role == owner)
  # ---------------------------------------------------------------------------
    Given path '/restheart-test/team-partitioned'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "title": "owned todo" }
    When method POST
    Then status 201

  # ---------------------------------------------------------------------------
  Scenario: active-team member is denied POST (predicate @user.team.role != owner)
  # ---------------------------------------------------------------------------
    * def memberEmail = 'tracl-member-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "MemberAcl1!" }
    When method PATCH
    Then status 200
    * def memberJwt = responseHeaders['Set-Cookie'][0].split('Bearer_')[1].split(';')[0]

    # member's JWT carries team.role == member → predicate fails → forbidden
    Given path '/restheart-test/team-partitioned'
    And header Authorization = 'Bearer ' + memberJwt
    And request { "title": "member todo" }
    When method POST
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: mergeRequest writes team as ObjectId from @user.team._id (data partitioning)
  # ---------------------------------------------------------------------------
    Given path '/restheart-test/team-partitioned'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "title": "partitioned todo" }
    When method POST
    Then status 201
    * def location = responseHeaders["Location"][0]
    * def docId = location.substring(location.lastIndexOf("/") + 1)

    # read back the document and verify the team field is the owner's team ObjectId
    Given path '/restheart-test/team-partitioned/' + docId
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    # team field must be a BSON ObjectId ({$oid: hex}), not a string
    And match response.team == { '$oid': '#string' }

    # also verify it equals the owners user.team._id
    * def teamId = response.team

    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.team._id == teamId
  # ---------------------------------------------------------------------------
  Scenario: promoting a member to owner syncs user.team.role (drives the next token)
  # ---------------------------------------------------------------------------
    * def memberEmail = 'tracl-promote-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "PromoteAcl1!" }
    When method PATCH
    Then status 200

    # before promotion: active-team role is member
    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.team.role == 'member'

    Given path '/auth/member-role'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "owner" }
    When method PATCH
    Then status 200

    # after promotion: user.team.role is synced to owner, so the member's next
    # issued/refreshed JWT (login, switch-team, ...) carries team.role == owner
    Given path '/users/' + memberEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.team.role == 'owner'
