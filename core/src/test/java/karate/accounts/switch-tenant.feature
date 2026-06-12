Feature: POST /auth/switch-tenant

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def ownerSetup = karate.callSingle('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = ownerSetup.ownerJwt
    * def secondSetup = karate.callSingle('classpath:karate/accounts/helpers/setup-second-team.feature')
    * def secondTenantId = secondSetup.secondTenantId
    # Accept the invitation (setup-second-team already invited owner-test)
    * def pendingInviteToken = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: 'owner-test@example.com' }).inviteToken
    * if (pendingInviteToken) karate.call('classpath:karate/accounts/helpers/accept-invite-clean.feature', { jwt: ownerJwt, token: pendingInviteToken })


  # ---------------------------------------------------------------------------
  Scenario: OPTIONS returns 200
  # ---------------------------------------------------------------------------
    Given path '/auth/switch-tenant'
    When method OPTIONS
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/switch-tenant'
    And request { "tenantId": "someid" }
    When method POST
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: missing tenantId returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/switch-tenant'
    And header Authorization = 'Bearer ' + ownerJwt
    And request {}
    When method POST
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: tenantId not in user's membership returns 403
  # ---------------------------------------------------------------------------
    Given path '/auth/switch-tenant'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "tenantId": "000000000000000000000000" }
    When method POST
    Then status 403

  # ---------------------------------------------------------------------------
  Scenario: switch to second tenant returns 200 with new cookie
  # ---------------------------------------------------------------------------
    Given path '/auth/switch-tenant'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "tenantId": "#(secondTenantId)" }
    When method POST
    Then status 200
    And match response.tenant == secondTenantId
    And match response.role == 'member'
    And match responseHeaders['Set-Cookie'] != null

  # ---------------------------------------------------------------------------
  Scenario: after switch, active tenant changes in GET /auth/tenants
  # ---------------------------------------------------------------------------
    # 1. Switch to second tenant
    Given path '/auth/switch-tenant'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "tenantId": "#(secondTenantId)" }
    When method POST
    Then status 200
    * def switchCookie = responseHeaders['Set-Cookie'].length > 0 ? responseHeaders['Set-Cookie'][0] : ''
    * def newJwt = switchCookie.split('Bearer_')[1].split(';')[0]

    # 2. Verify new JWT has the new active tenant
    Given path '/auth/tenants'
    And header Authorization = 'Bearer ' + newJwt
    When method GET
    Then status 200
    * def activeTenants = karate.filter(response, function(x){ return x.active == true })
    * assert activeTenants.length == 1
    And match activeTenants[0].id == secondTenantId

  # ---------------------------------------------------------------------------
  Scenario: switch back to original tenant works
  # ---------------------------------------------------------------------------
    # Get original tenant
    Given path '/auth/tenants'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    * def originalTenant = karate.filter(response, function(x){ return x.active == true })[0]
    * def originalTenantId = originalTenant.id

    # Switch to second tenant
    Given path '/auth/switch-tenant'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "tenantId": "#(secondTenantId)" }
    When method POST
    Then status 200
    * def switchCookie = responseHeaders['Set-Cookie'].length > 0 ? responseHeaders['Set-Cookie'][0] : ''
    * def newJwt = switchCookie.split('Bearer_')[1].split(';')[0]

    # Switch back
    Given path '/auth/switch-tenant'
    And header Authorization = 'Bearer ' + newJwt
    And request { "tenantId": "#(originalTenantId)" }
    When method POST
    Then status 200
    And match response.tenant == originalTenantId

  # ---------------------------------------------------------------------------
  Scenario: verify DB — invite with existing user adds tenants entry
  # ---------------------------------------------------------------------------
    # Check owner-test has orgs array with both teams
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    When method GET
    Then status 200
    And match response.tenants == '#array'
    * assert response.tenants.length >= 2
    * def tenantIds = karate.map(response.tenants, function(x){ return x.id })
    And match tenantIds contains secondTenantId
