Feature: PATCH /auth/change-password

  # Tests for the in-session password-change endpoint.
  # X-Skip-Email: true is set globally in karate-config.js.
  #
  # Since Karate has no bcrypt helper to verify the stored hash directly, the
  # "new password actually took effect" checks re-invoke this same endpoint
  # (which itself bcrypt-verifies currentPassword server-side): using the old
  # password as currentPassword should now fail, and using the new password
  # as currentPassword should now succeed.

  Background:
    * url baseUrl
    * configure followRedirects = false
    * def setupResult = karate.call('classpath:karate/accounts/helpers/setup-owner.feature')
    * def ownerJwt = setupResult.ownerJwt

  # ---------------------------------------------------------------------------
  Scenario: OPTIONS returns 200
  # ---------------------------------------------------------------------------
    Given path '/auth/change-password'
    When method OPTIONS
    Then status 200

  # ---------------------------------------------------------------------------
  Scenario: unauthenticated request returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/change-password'
    And request { "currentPassword": "OwnerPass123!", "newPassword": "NewPass456!" }
    When method PATCH
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: missing currentPassword returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/change-password'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "newPassword": "NewPass456!" }
    When method PATCH
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: missing newPassword returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/change-password'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "currentPassword": "OwnerPass123!" }
    When method PATCH
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: new password too short returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/change-password'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "currentPassword": "OwnerPass123!", "newPassword": "short" }
    When method PATCH
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: wrong currentPassword returns 401
  # ---------------------------------------------------------------------------
    Given path '/auth/change-password'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "currentPassword": "NotTheRealPassword!", "newPassword": "NewPass456!" }
    When method PATCH
    Then status 401

  # ---------------------------------------------------------------------------
  Scenario: happy path — change password, then verify old fails and new works
  # ---------------------------------------------------------------------------
    Given path '/auth/change-password'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "currentPassword": "OwnerPass123!", "newPassword": "NewPass456!" }
    When method PATCH
    Then status 200

    # Old password no longer works
    Given path '/auth/change-password'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "currentPassword": "OwnerPass123!", "newPassword": "AnotherPass789!" }
    When method PATCH
    Then status 401

    # New password works
    Given path '/auth/change-password'
    And header Authorization = 'Bearer ' + ownerJwt
    And request { "currentPassword": "NewPass456!", "newPassword": "AnotherPass789!" }
    When method PATCH
    Then status 200
