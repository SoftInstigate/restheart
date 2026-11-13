Feature: mongo.sort, and $ifvar in sort and readFilter

  Since 9.16. The permission declares the alternatives, the client picks one by name or by value:
  a read with no query of its own gets an order and a condition the permission states.

Background:
    * url baseUrl
    * callonce read('permission-sort-setup.feature')
    * def Base64 = Java.type('java.util.Base64')
    * def user = 'Basic ' + Base64.getEncoder().encodeToString('allow-filter-user:secret'.getBytes())
    * def products = '/test-permission-sort/products'

Scenario: with no choice, the default order and the mandatory condition

    * header Authorization = user
    Given path products
    And param rep = 's'
    When method GET
    Then status 200
    And match response[*]._id == ['c', 'b', 'a']

Scenario: the client picks one of the declared orders

    * header Authorization = user
    Given path products
    And param order = 'by-name'
    And param rep = 's'
    When method GET
    Then status 200
    And match response[*]._id == ['b', 'a', 'c']

Scenario: an order the permission does not declare is refused

    * header Authorization = user
    Given path products
    And param order = 'price'
    When method GET
    Then status 400
    And match response.message contains 'by-name'

Scenario: the optional condition applies when the client gives its value

    * header Authorization = user
    Given path products
    And param category = 'kitchen'
    And param order = 'by-name'
    And param rep = 's'
    When method GET
    Then status 200
    And match response[*]._id == ['b', 'a']

Scenario: a value from the request is a value, never a query

    * header Authorization = user
    Given path products
    And param category = '{"$ne": null}'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == []

Scenario: the client cannot send a sort of its own

    * header Authorization = user
    Given path products
    And param sort = '{"n": 1}'
    When method GET
    Then status 403

Scenario: a plain name is a query parameter, with its default

    * header Authorization = user
    Given path '/test-permission-sort/plain'
    And param rep = 's'
    When method GET
    Then status 200
    # the kitchen ones by default, newest n first
    And match response[*]._id == ['b', 'a']

    * header Authorization = user
    Given path '/test-permission-sort/plain'
    And param cat = 'office'
    And param dir = '1'
    And param rep = 's'
    When method GET
    Then status 200
    And match response[*]._id == ['c', 'd']

Scenario: a query parameter that carries an operator is refused

    * header Authorization = user
    Given path '/test-permission-sort/plain'
    And param cat = '{"$ne": null}'
    When method GET
    Then status 400

Scenario: a permission whose mongo block is not valid is refused when written

    * header Authorization = adminAuth
    Given path '/restheart-test/acl/permissionSortInvalid'
    And param wm = 'upsert'
    And request { "roles": ["allow-filter-role"], "predicate": "path-prefix('/nowhere') and method(GET)", "priority": 100, "mongo": { "sort": { "_id": { "$var": [1] } } } }
    When method PUT
    Then status 400
    And match response.message contains '$var takes'

Scenario: a stored permission that is not valid allows nothing

    * header Authorization = user
    Given path '/test-permission-sort/broken'
    When method GET
    Then status 403
