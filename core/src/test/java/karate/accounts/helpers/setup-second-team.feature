Feature: setup second team for multi-tenant tests

  # Deletes second-team-owner@example.com first so each run starts fresh on any DB.
  # Creates second-team-owner@example.com (owner of "Second Test Team"),
  # then invites owner-test@example.com into it with role=member.
  # Returns: { secondOwnerJwt, secondTenantId }

  @helper
  Scenario: create second team and invite owner-test into it
    * url baseUrl
    * configure followRedirects = false
    * def secondOwnerJwt = ''

    # 0. Cleanup — delete second-team-owner if it exists from a previous run
    Given path '/users/second-team-owner@example.com'
    And header Authorization = adminAuth
    When method DELETE
    * match [200, 204, 404] contains responseStatus

    # 1. Register fresh
    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Second",
        "lastName":  "Owner",
        "teamName":  "Second Test Team",
        "email":     "second-team-owner@example.com",
        "password":  "SecondPass123!"
      }
      """
    When method POST
    Then status 201

    # 2. Fetch user doc to get emailVerificationToken and tenant
    Given path '/users/second-team-owner@example.com'
    And header Authorization = adminAuth
    When method GET
    Then status 200
    # tenant may be stored as ObjectId {"$oid":"..."} or plain string — normalise to hex string
    * def rawTenant = response.tenant
    * def secondTenantId = (typeof rawTenant == 'object') ? rawTenant['$oid'] : rawTenant
    * def verifyToken = response.emailVerificationToken

    # 3. Verify email — get JWT from Set-Cookie
    * def verifyResult = karate.call('classpath:karate/accounts/helpers/setup-second-team-verify.feature', { token: verifyToken })
    * def secondOwnerJwt = verifyResult.secondOwnerJwt

    # 4. Invite owner-test@example.com into the second team
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + secondOwnerJwt
    And request { "email": "owner-test@example.com", "role": "member" }
    When method POST
    Then status 201

    * karate.log('Second team setup done, tenantId:', secondTenantId, 'jwt present:', secondOwnerJwt != '')
