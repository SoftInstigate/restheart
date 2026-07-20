Feature: DefaultMembershipProvider uses override-users-db per-request

  # Verifies that getMembershipProvider(req) correctly resolves the database
  # from the per-request override-users-db param (attached by DbOverrideInterceptor
  # when ?_db-override=<db> is present), rather than from the static accountsConfig.db.
  #
  # This replicates the shared-tier deployment scenario where accountsConfig.db is
  # blank and the real database is injected per-request by AuthDbResolver.
  #
  # All accounts calls include ?_db-override=restheart-test.
  # DbOverrideInterceptor (enabled in conf-overrides.yml) attaches override-users-db
  # to those requests only — existing tests are unaffected.

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def dbOverride = 'test-auth-db-override'
    * def ownerEmail = 'db-override-owner-' + java.util.UUID.randomUUID() + '@example.com'
    * def inviteeEmail = 'db-override-invitee-' + java.util.UUID.randomUUID() + '@example.com'
    * def password = 'Password123!'

  # ---------------------------------------------------------------------------
  Scenario: register + verify + invite succeed when database is resolved per-request
  # ---------------------------------------------------------------------------

    # 1. Register owner (with ?_db-override → override-users-db is attached)
    Given path '/auth/register'
    And param _db-override = dbOverride
    And request
      """
      {
        "firstName": "Override",
        "lastName":  "Owner",
        "teamName":  "Override Team",
        "email":     "#(ownerEmail)",
        "password":  "#(password)"
      }
      """
    When method POST
    Then status 201

    # 2. Fetch verification token via admin
    Given path '/' + dbOverride + '/users/' + ownerEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * def verifyToken = response.emailVerificationToken

    # 3. Verify email (with override) → should set roles=["user"], create team and issue JWT cookie
    Given path '/auth/verify'
    And param _db-override = dbOverride
    And param email = ownerEmail
    And param token = verifyToken
    When method GET
    * match [200, 201, 302] contains responseStatus
    * def setCookieList = responseHeaders['Set-Cookie']
    * def setCookie = setCookieList != null && setCookieList.length > 0 ? setCookieList[0] : ''
    * def ownerJwt = setCookie.indexOf('Bearer_') >= 0 ? setCookie.split('Bearer_')[1].split(';')[0] : ''

    # 4. Confirm user document has team field (proves createInitialTeam ran correctly)
    Given path '/' + dbOverride + '/users/' + ownerEmail
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * match response.team._id == '#present'
    * match response.team.role == 'owner'
    * match response.teams == '#[1]'
    * match response.teams[0].role == 'owner'
    * def teamOid = response.teams[0].id['$oid']

    # 5. Confirm team document exists in the overridden database's teams collection
    Given path '/' + dbOverride + '/teams/' + teamOid
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    * match response.name == 'Override Team'
    * match response.createdBy == ownerEmail
    * match response.members == '#[1]'
    * match response.members[0].userId == ownerEmail
    * match response.members[0].role == 'owner'

    # 6. Invite a new user (with override) — requires owner role in active team
    Given path '/auth/invite'
    And param _db-override = dbOverride
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(inviteeEmail)", "role": "member" }
    When method POST
    Then status 201
