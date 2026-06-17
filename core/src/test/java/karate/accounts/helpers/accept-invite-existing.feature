Feature: accept an existing user invitation — read token, accept, verify cleanup

  Background:
    * url baseUrl
    * configure followRedirects = false

  @helper
  Scenario: read invitation token and accept
    # Read invitation token from auth_invitations collection via admin REST API
    Given path '/auth_invitations'
    And header Authorization = adminAuth
    And param filter = '{"email":"owner-test@example.com"}'
    And param pagesize = 1
    And param sort = '{"_id":-1}'
    And param rep = 's'
    When method GET
    Then status 200
    And assert response.length > 0
    * def inviteDoc = response[0]
    * def inviteToken = inviteDoc.token

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
