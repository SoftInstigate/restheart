Feature: setup second team for multi-tenant tests

  # Idempotent: safe to call multiple times via karate.callSingle.
  # Creates second-team-owner@example.com (owner of "Second Test Team"),
  # then invites owner-test@example.com into it with role=user.
  # Returns: { secondOwnerJwt, secondTenantId }

  @helper
  Scenario: create second team and invite owner-test into it
    * url baseUrl
    * configure followRedirects = false
    * def secondOwnerJwt = ''

    # 1. Register second owner (409 = already exists, that's fine)
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

    # 2. Fetch user doc to get tokens/tenant
    Given path '/users/second-team-owner@example.com'
    And header Authorization = adminAuth
    When method GET
    Then status 200
    * def secondTenantId = response.tenant
    * def verifyToken = response.emailVerificationToken

    # 3. Verify email — extract JWT from Set-Cookie (same pattern as setup-owner.feature).
    #    If verifyToken is absent (user already active) skip the call.
    * def verifyResult = verifyToken ? karate.call('classpath:karate/accounts/helpers/setup-second-team-verify.feature', { token: verifyToken }) : null
    * def secondOwnerJwt = verifyResult != null && verifyResult.secondOwnerJwt ? verifyResult.secondOwnerJwt : secondOwnerJwt

    # 4. Fallback: if JWT not obtained from verify cookie (user was already active),
    #    login via GET /token using pre-computed Basic auth from karate-config.js.
    * def loginResult = (!secondOwnerJwt) ? karate.call('classpath:karate/accounts/helpers/setup-second-team-login.feature') : null
    * def secondOwnerJwt = loginResult != null && loginResult.secondOwnerJwt ? loginResult.secondOwnerJwt : secondOwnerJwt

    # 5. Invite owner-test@example.com (409 = already invited/member, that's fine)
    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + secondOwnerJwt
    And request { "email": "owner-test@example.com", "role": "user" }
    When method POST
    * match [201, 409] contains responseStatus

    * karate.log('Second team setup done, tenantId:', secondTenantId, 'jwt present:', secondOwnerJwt != '')
