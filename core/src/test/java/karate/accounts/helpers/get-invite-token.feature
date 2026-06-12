Feature: helper — get invite token for a user

  @helper
  Scenario: get invite token
    * url baseUrl
    Given path '/users/' + __arg.email
    And header Authorization = adminAuth
    When method GET
    * def inviteToken = response.inviteToken
