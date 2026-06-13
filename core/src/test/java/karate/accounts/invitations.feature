Feature: Invitations — new and existing users

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def ownerSetup = karate.callSingle('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = ownerSetup.ownerJwt
    * def secondSetup = karate.callSingle('classpath:karate/accounts/helpers/setup-second-team.feature')
    * def secondOwnerJwt = secondSetup.secondOwnerJwt
    * def secondTenantId = secondSetup.secondTenantId

  # ---------------------------------------------------------------------------
  Scenario: invite a new user creates $unauthenticated account
  # ---------------------------------------------------------------------------
    * def newUserEmail = 'invite-new-' + java.lang.System.currentTimeMillis() + '@example.com'
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + secondOwnerJwt
    And request { "email": "#(newUserEmail)", "role": "member" }
    When method POST
    Then status 201

    Given path '/users/' + newUserEmail
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.roles contains '$unauthenticated'

  # ---------------------------------------------------------------------------
  Scenario: invite existing user and accept via /auth/accept-invite
  # ---------------------------------------------------------------------------
    # Invite owner-test into second team
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + secondOwnerJwt
    And request { "email": "owner-test@example.com", "role": "member" }
    When method POST
    Then assert responseStatus == 201 || responseStatus == 409

    # Read invitation token from auth_invitations collection via admin REST API
    Given path '/auth_invitations'
    And header Authorization = adminAuth
    And param filter = '{"email":"owner-test@example.com"}'
    And param pagesize = 1
    And param sort = '{"_id":-1}'
    And param rep = 's'
    When method GET
    Then status 200
    * def inviteDoc = response[0]
    * def inviteToken = inviteDoc.token

    # Get tenants count before acceptance
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def tenantsBefore = response.tenants ? response.tenants.length : 0

    # Accept invitation via helper (clean session, no cookie)
    * def acceptResult = karate.call('classpath:karate/accounts/helpers/accept-invite-clean.feature', { jwt: ownerJwt, token: inviteToken })

    # Verify invitation was deleted
    Given path '/auth_invitations'
    And header Authorization = adminAuth
    And param filter = '{"email":"owner-test@example.com","orgId":{"$oid":"' + secondTenantId['$oid'] + '"}}'
    And param rep = 's'
    When method GET
    Then status 200
    * def remaining = response ? response.length : 0
    * match remaining == 0

    # Verify tenant was added to user
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def tenantIds = karate.map(response.tenants, function(x){ return x.id })
    And match tenantIds contains secondTenantId

  # ---------------------------------------------------------------------------
  Scenario: invalid token returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/accept-invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "token": "invalid-token-12345" }
    When method POST
    Then status 401
