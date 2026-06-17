Feature: helper — accept invitation without cookie

  @helper
  Scenario: accept invitation
    * url baseUrl
    * configure followRedirects = false
    # Do NOT set any cookie — use only the Authorization header
    Given path '/auth/accept-invite'
    And header Authorization = 'Bearer ' + __arg.jwt
    And request { "token": "#(__arg.token)" }
    When method POST
    Then status 200
