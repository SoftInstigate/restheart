@ignore
Feature: Setup test data for aggregation

Background:
    * url 'http://localhost:8080'

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


Scenario: Create collection with mock data in gql-apps

    # create test-aggregations database
    * header Authorization = admin
    Given path '/test-aggregations'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200


    # create test collection
    * header Authorization = admin
    Given path '/test-aggregations/test'
    And request read('aggregation-def.json')
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # insert data
    * header Authorization = admin
    Given path '/test-aggregations/test'
    And request read('data.json')
    When method POST