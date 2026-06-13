Feature: helper — get invite token from auth_invitations

  @helper
  Scenario: get invite token
    * url baseUrl
    Given path '/auth_invitations'
    And header Authorization = adminAuth
    And param rep = 's'
    And param filter = '{"email":"' + __arg.email + '"}'
    And param pagesize = 1
    When method GET
    * def result = response.length > 0 ? response[0].token : null
