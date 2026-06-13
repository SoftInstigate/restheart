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
    Then status 201

    # 5. Accept the invitation as owner-test
    #    Read token from auth_invitations (as admin, no cookie conflict)
    Given path '/auth_invitations'
    And header Authorization = adminAuth
    And param filter = '{"email":"owner-test@example.com"}'
    And param pagesize = 1
    And param sort = '{"_id":-1}'
        And param rep = 's'
When method GET
    Then status 200
    * def inviteToken = response[0].token

    #    Login as owner-test to get JWT
    * def ownerLogin = karate.callSingle('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerAcceptJwt = ownerLogin.ownerJwt

    #    Accept — inline, no cookie inheritance
    Given path '/auth/accept-invite'
    And header Authorization = 'Bearer ' + ownerAcceptJwt
    And request { "token": "#(inviteToken)" }
    When method POST
    Then status 200

    * karate.log('Second team setup done, tenantId:', secondTenantId)
