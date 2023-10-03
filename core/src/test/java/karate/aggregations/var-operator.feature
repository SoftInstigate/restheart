Feature: Test $var aggregation operator

Background:
    * url 'http://localhost:8080'
    * path '/test-aggregations/test/_aggrs/test'

    * def basic =
    """
    function(creds) {
    var temp = creds.username + ':' + creds.password;
    var Base64 = Java.type('java.util.Base64');
    var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
    return 'Basic ' + encoded;
    }
    """

    * def admin = basic({username: 'admin', password: 'secret'})

    * def setupData = callonce read('setup.feature')


Scenario: Should return 3 docs

    And header Authorization = admin
    When method GET
    Then status 200
    And match $._returned == 3

Scenario: Should return 3 docs

   And header Authorization = admin
   And param avars = '{ "options": {"skip": 0, "limit": 100 }}'
   When method GET
   Then status 200
   And match $._returned == 3

Scenario: Should return 2 docs

    And header Authorization = admin
    And param avars = '{ "options": {"skip": 1, "limit": 2 }}'
    When method GET
    Then status 200
    And match $._returned == 2

Scenario: Should return 3 docs with first having n==3

    And header Authorization = admin
    And param avars = '{ "options": {"sort": {"n": -1}}}'
    When method GET
    Then status 200
    And match $._returned == 3
    And match $._embedded.rh:result[0].n == 3

Scenario: Should return 1 doc with first having n==1

    And header Authorization = admin
    And param avars = '{ "options": {"limit":1, "sort": {"n": 1}}}'
    When method GET
    Then status 200
    And match $._returned == 1
    And match $._embedded.rh:result[0].n == 1

