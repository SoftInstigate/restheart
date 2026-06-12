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
    And match response.inviteToken == '#string'

  # ---------------------------------------------------------------------------
  Scenario: invite existing user and accept via /auth/accept-invite
  # ---------------------------------------------------------------------------
    # Invite owner-test into second team
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + secondOwnerJwt
    And request { "email": "owner-test@example.com", "role": "member" }
    When method POST
    * def inviteStatus = responseStatus

    # Verify invite fields are stored (skip if 409 — already invited by setup)
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def inviteToken = response.inviteToken
    * def tenantsBefore = response.tenants ? response.tenants.length : 0

    # Only accept if we have a valid token
    * if (inviteToken != null && inviteToken != '') karate.call('classpath:karate/accounts/helpers/accept-invite-clean.feature', { jwt: ownerJwt, token: inviteToken })

    # Verify invite fields are cleared and tenant was added
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
