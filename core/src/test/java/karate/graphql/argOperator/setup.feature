@ignore
Feature: Setup test data for rootDoc

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

    # create test-graphql database
    * header Authorization = admin
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200


    # create gql-apps collection
    * header Authorization = admin
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200


    # create document with a graphql app definition
    * header Authorization = admin
    Given path '/test-graphql/gql-apps/test-arg-operator'
    And param wm = "upsert"
    And request read('app-definition.json')
    When method PUT

    # crete data collection
    * header Authorization = admin
    Given path '/test-graphql/test-arg-operator'
    When method PUT

    # insert data
    * header Authorization = admin
    Given path '/test-graphql/test-arg-operator'
    And request read('data.json')
    When method POST