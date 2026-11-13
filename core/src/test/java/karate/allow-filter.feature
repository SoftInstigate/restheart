Feature: mongo.allowFilter decides whether a read may carry filter, sort and sort_by

  Since 9.16. A read with one of them, through a permission that does not allow it, does not match
  the permission and gets 403; the application queries through aggregations. The root role is not
  affected. A permission that does not set allowFilter takes /mongo/default-allow-filter,
  false since 9.16 and set to true for this suite in conf-overrides.yml.

Background:
    * url baseUrl
    * callonce read('allow-filter-setup.feature')
    * def Base64 = Java.type('java.util.Base64')
    * def user = 'Basic ' + Base64.getEncoder().encodeToString('allow-filter-user:secret'.getBytes())

Scenario: allowFilter false refuses filter, sort and sort_by, and allows the plain read

    * header Authorization = user
    Given path '/test-allow-filter/denied'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'

    * header Authorization = user
    Given path '/test-allow-filter/denied'
    And param filter = '{"status":"A"}'
    When method GET
    Then status 403
    And match response.message == 'filter, sort and sort_by are not allowed'

    * header Authorization = user
    Given path '/test-allow-filter/denied/_size'
    And param filter = '{"status":"A"}'
    When method GET
    Then status 403
    And match response.message == 'filter, sort and sort_by are not allowed'

    * header Authorization = user
    Given path '/test-allow-filter/denied'
    And param sort = '{"status":1}'
    When method GET
    Then status 403
    And match response.message == 'filter, sort and sort_by are not allowed'

    * header Authorization = user
    Given path '/test-allow-filter/denied'
    And param sort_by = 'status'
    When method GET
    Then status 403
    And match response.message == 'filter, sort and sort_by are not allowed'

Scenario: with allowFilter false the aggregation answers the query

    * header Authorization = user
    Given path '/test-allow-filter/denied/_aggrs/byStatus'
    And param status = 'A'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'

Scenario: allowFilter true allows filter and sort

    * header Authorization = user
    Given path '/test-allow-filter/allowed'
    And param filter = '{"status":"A"}'
    And param sort = '{"status":1}'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'

Scenario: a permission without allowFilter takes the default, true in this suite

    * header Authorization = user
    Given path '/test-allow-filter/unset'
    And param filter = '{"status":"A"}'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'

Scenario: the root role can always use filter

    * header Authorization = adminAuth
    Given path '/test-allow-filter/denied'
    And param filter = '{"status":"A"}'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'
