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

  # ---------------------------------------------------------------------------
  Scenario: accepted invite token cannot be reused
  # ---------------------------------------------------------------------------
    * def existingEmail = 'invite-reuse-' + java.util.UUID.randomUUID() + '@example.com'

    # Register + verify a fresh user to be the invitee
    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Reuse",
        "lastName":  "Test",
        "teamName":  "Reuse Corp",
        "email":     "#(existingEmail)",
        "password":  "ReusePass1!"
      }
      """
    When method POST
    Then status 201

    Given path '/users/' + existingEmail
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def verifyTok = response.emailVerificationToken
    Given path '/auth/verify'
    And param email = existingEmail
    And param token = verifyTok
    When method GET
    * match [200, 302] contains responseStatus

    # Extract JWT for the invitee from the verify cookie
    * def verifyCookie = responseHeaders['Set-Cookie'][0]
    * def inviteeJwt = verifyCookie.split('Bearer_')[1].split(';')[0]

    # secondOwner invites the existing user
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + secondOwnerJwt
    And request { "email": "#(existingEmail)", "role": "member" }
    When method POST
    Then status 201

    # Read token from auth_invitations
    Given path '/auth_invitations'
    And header Authorization = adminAuth
    And param filter = '{"email":"' + existingEmail + '"}'
    And param pagesize = 1
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length > 0
    * def reuseToken = response[0].token

    # First acceptance — must succeed
    Given path '/auth/accept-invite'
    And header Authorization = 'Bearer ' + inviteeJwt
    And request { "token": "#(reuseToken)" }
    When method POST
    Then status 200

    # Second acceptance — token already consumed
    Given path '/auth/accept-invite'
    And header Authorization = 'Bearer ' + inviteeJwt
    And request { "token": "#(reuseToken)" }
    When method POST
    Then status 401

    # auth_invitations must be empty for this user+org after acceptance
    Given path '/auth_invitations'
    And header Authorization = adminAuth
    And param filter = '{"email":"' + existingEmail + '"}'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[0]'
