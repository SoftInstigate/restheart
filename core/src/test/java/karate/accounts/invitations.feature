Feature: Invitations — new and existing users

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def ownerSetup = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = ownerSetup.ownerJwt
    * def secondSetup = karate.call('classpath:karate/accounts/helpers/setup-second-team.feature')
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
    * def inviteStatus = responseStatus

    # Only proceed with acceptance if a fresh invite was created (201).
    # If 409, the user is already a member — skip token lookup and acceptance.
    * if (inviteStatus == 201) karate.call('classpath:karate/accounts/helpers/accept-invite-existing.feature', { secondOwnerJwt: secondOwnerJwt, secondTenantId: secondTenantId, ownerJwt: ownerJwt })

    # Verify tenant is present for user (whether newly accepted or already a member)
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def tenantIds = response.tenants ? karate.map(response.tenants, function(x){ return x.id }) : []
    And match tenantIds contains secondTenantId

  # ---------------------------------------------------------------------------
  Scenario: invalid token returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/accept-invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "token": "invalid-token-12345" }
    When method POST
    Then status 401
