Feature: helper — accept invitation via Bearer token

  @helper
  Scenario: accept invitation
    * url baseUrl
    * configure followRedirects = false
    Given path '/auth/accept-invite'
    And header Authorization = 'Bearer ' + __arg.jwt
    And request { "token": "#(__arg.token)" }
    When method POST
    Then status 200
