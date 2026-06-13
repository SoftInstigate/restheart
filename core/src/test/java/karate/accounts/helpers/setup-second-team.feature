Feature: setup second team for multi-tenant tests

  @helper
  Scenario: create second team and invite owner-test into it
    * url baseUrl
    * configure followRedirects = false

    # 0. Cleanup
    Given path '/users/second-team-owner@example.com'
    And header Authorization = adminAuth
    When method DELETE
    * match [200, 204, 404] contains responseStatus

    # 1. Register fresh
    Given path '/auth/register'
    And request { "firstName": "Second", "lastName": "Owner", "teamName": "Second Test Team", "email": "second-team-owner@example.com", "password": "SecondPass123!" }
    When method POST
    Then status 201

    # 2. Get verification token
    Given path '/users/second-team-owner@example.com'
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def secondTenantId = response.tenant
    * def verifyToken = response.emailVerificationToken

    # 3. Verify email
    * def verifyResult = karate.call('classpath:karate/accounts/helpers/setup-second-team-verify.feature', { token: verifyToken })
    * def secondOwnerJwt = verifyResult.secondOwnerJwt

    # 4. Invite owner-test
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + secondOwnerJwt
    And request { "email": "owner-test@example.com", "role": "member" }
    When method POST
    * def inviteStatus = responseStatus

    # 5. Accept the invitation as owner-test (only if fresh invite created)
    #    Login as owner-test to get JWT
    * def ownerLogin = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerAcceptJwt = ownerLogin.ownerJwt

    * if (inviteStatus == 201) karate.call('classpath:karate/accounts/helpers/accept-invite-existing.feature', { secondOwnerJwt: secondOwnerJwt, secondTenantId: secondTenantId, ownerJwt: ownerAcceptJwt })

    # Verify owner-test is in the second team (whether just accepted or already a member)
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def tenantIds = karate.map(response.tenants, function(x){ return x.id })
    And match tenantIds contains secondTenantId

    * karate.log('Second team setup done, tenantId:', secondTenantId)
