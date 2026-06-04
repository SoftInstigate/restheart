Feature: login second-team-owner@example.com via /token

  @helper
  Scenario: GET /token with pre-computed Basic auth
    * url baseUrl
    * configure followRedirects = false

    Given path '/token'
    And header Authorization = secondOwnerAuth
    And header No-Auth-Challenge = 'true'
    When method GET
    Then status 200
    * def secondOwnerJwt = response.access_token
    * karate.log('Logged in second-team-owner@example.com via /token')
