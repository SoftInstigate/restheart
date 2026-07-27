Feature: PATCH /auth/profile

  # Tests for the self-service profile update endpoint.
  # X-Skip-Email: true is set globally in karate-config.js.

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def setupResult = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = setupResult.ownerJwt

  # ---------------------------------------------------------------------------
  Scenario: OPTIONS returns 200
  # ---------------------------------------------------------------------------
    Given path '/auth/profile'
    When method OPTIONS
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/profile'
    And request { "firstName": "New" }
    When method PATCH
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: empty body returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/profile'
    And header Authorization = 'Bearer ' + ownerJwt
    And request {}
    When method PATCH
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: blank firstName returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/profile'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "firstName": "   " }
    When method PATCH
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: happy path — updates both firstName and lastName
  # ---------------------------------------------------------------------------
    Given path '/auth/profile'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "firstName": "Alice", "lastName": "Wonderland" }
    When method PATCH
    Then status 200

    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.profile.name == 'Alice'
    And match response.profile.surname == 'Wonderland'

  # ---------------------------------------------------------------------------
  Scenario: partial update — only lastName changes, firstName untouched
  # ---------------------------------------------------------------------------
    Given path '/auth/profile'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "lastName": "Smith" }
    When method PATCH
    Then status 200

    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.profile.name == 'Owner'
    And match response.profile.surname == 'Smith'

  # ---------------------------------------------------------------------------
  Scenario: a caller can only update their own profile, not someone else's
  # ---------------------------------------------------------------------------
    * def memberEmail = 'up-isolation-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/invite'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "email": "#(memberEmail)", "role": "member" }
    When method POST
    Then status 201

    * def tokenResult = karate.call('classpath:karate/accounts/helpers/get-invite-token.feature', { email: memberEmail })
    * def inviteToken = tokenResult.result

    Given path '/auth/activate'
    And request { "email": "#(memberEmail)", "token": "#(inviteToken)", "password": "UpdateProfile1!" }
    When method PATCH
    Then status 200
    * def memberJwt = responseHeaders['Set-Cookie'][0].split('Bearer_')[1].split(';')[0]

    Given path '/auth/profile'
    And header Authorization = 'Bearer ' + memberJwt
    And request { "firstName": "Member Only" }
    When method PATCH
    Then status 200

    # owner-test's own profile is unaffected
    Given path '/users/owner-test@example.com'
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.profile.name == 'Owner'
