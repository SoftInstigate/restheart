@ignore
Feature: Setup test app for union

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


Scenario: Create test app for unions

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
    Given path '/test-graphql/gql-apps/test-union'
    And param wm = "upsert"
    And request read('unionTestApp.json')
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200