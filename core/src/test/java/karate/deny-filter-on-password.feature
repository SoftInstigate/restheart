Feature: a filter or a sort on the password or the one-time tokens of the users is refused

  denyFilterOnUserPwd refuses, with 403, a request on the users collection of
  mongoRealmAuthenticator whose filter or sort refers to the password, at any depth: inside $or,
  $and and $nor too, and as a field path in $expr. Since 9.17.0; before, only keys in nested
  documents were checked, and only on a GET.

Background:
    * url baseUrl
    * def users = '/users'

Scenario: a condition on the password is refused

    Given path users
    And header Authorization = adminAuth
    And param filter = '{"password":{"$exists":true}}'
    When method GET
    Then status 403

Scenario: a condition on the password inside a logical operator is refused

    Given path users
    And header Authorization = adminAuth
    And param filter = '{"$or":[{"_id":"nobody"},{"password":{"$exists":true}}]}'
    When method GET
    Then status 403

    Given path users
    And header Authorization = adminAuth
    And param filter = '{"$and":[{"$nor":[{"password":{"$exists":false}}]}]}'
    When method GET
    Then status 403

Scenario: the password in an expression is refused

    Given path users
    And header Authorization = adminAuth
    And param filter = '{"$expr":{"$gt":[{"$strLenCP":"$password"},0]}}'
    When method GET
    Then status 403

Scenario: a sort on the password is refused

    Given path users
    And header Authorization = adminAuth
    And param sort = '{"password":1}'
    When method GET
    Then status 403

    Given path users
    And header Authorization = adminAuth
    And param sort_by = '-password'
    When method GET
    Then status 403

Scenario: a filter on the other fields is allowed

    Given path users
    And header Authorization = adminAuth
    And param filter = '{"_id":"nobody"}'
    And param rep = 's'
    When method GET
    Then status 200

Scenario: a bulk write with a condition on the password is refused

    # the number of documents a bulk write matches would tell about the hash as a read does;
    # the filters match no document, so that a request that is not refused changes nothing
    Given path users + '/*'
    And header Authorization = adminAuth
    And param filter = '{"$and":[{"_id":"nobody"},{"password":{"$exists":true}}]}'
    And request { "$set": { "denyFilterProbe": true } }
    When method PATCH
    Then status 403

    Given path users + '/*'
    And header Authorization = adminAuth
    And param filter = '{"$and":[{"_id":"nobody"},{"$expr":{"$gt":[{"$strLenCP":"$password"},0]}}]}'
    When method DELETE
    Then status 403

Scenario: a bulk write with a filter on the other fields is allowed

    Given path users + '/*'
    And header Authorization = adminAuth
    And param filter = '{"_id":"nobody"}'
    And request { "$set": { "denyFilterProbe": true } }
    When method PATCH
    Then status 200
    And match response._embedded['rh:result'][0].matched == 0

Scenario: a filter or a sort on the one-time tokens of the users is refused

    # denyFilterOnAccountTokens, since 9.15.3
    Given path users
    And header Authorization = adminAuth
    And param filter = '{"emailVerificationToken":{"$exists":true}}'
    When method GET
    Then status 403

    Given path users
    And header Authorization = adminAuth
    And param filter = '{"$or":[{"_id":"nobody"},{"passwordResetToken":"x"}]}'
    When method GET
    Then status 403

    Given path users
    And header Authorization = adminAuth
    And param filter = '{"$expr":{"$eq":["$inviteToken","x"]}}'
    When method GET
    Then status 403

    Given path users
    And header Authorization = adminAuth
    And param sort_by = 'passwordResetToken'
    When method GET
    Then status 403
