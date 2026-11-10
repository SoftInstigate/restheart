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
