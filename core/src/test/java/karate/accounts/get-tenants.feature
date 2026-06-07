Feature: GET /auth/tenants

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def ownerSetup = karate.callSingle('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = ownerSetup.ownerJwt
    * def secondSetup = karate.callSingle('classpath:karate/accounts/helpers/setup-second-team.feature')
    * def secondTenantId = secondSetup.secondTenantId

  # ---------------------------------------------------------------------------
  Scenario: OPTIONS returns 200
  # ---------------------------------------------------------------------------
    Given path '/auth/tenants'
    When method OPTIONS
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/tenants'
    When method GET
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: authenticated user gets their tenants list
  # ---------------------------------------------------------------------------
    Given path '/auth/tenants'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    And match each response == { id: '#notnull', name: '#string', role: '#string', active: '#boolean' }

  # ---------------------------------------------------------------------------
  Scenario: exactly one entry has active=true (the current JWT tenant)
  # ---------------------------------------------------------------------------
    Given path '/auth/tenants'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    * def activeTenants = karate.filter(response, function(x){ return x.active == true })
    * assert activeTenants.length == 1

  # ---------------------------------------------------------------------------
  Scenario: user with two tenants sees both in the list
  # ---------------------------------------------------------------------------
    Given path '/auth/tenants'
    And header Authorization = 'Bearer ' + ownerJwt
    When method GET
    Then status 200
    * assert response.length >= 2
    * def ids = karate.map(response, function(x){ return x.id })
    And match ids contains secondTenantId
