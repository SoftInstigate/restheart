@ignore
Feature: Cleaup test data

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


Scenario: Delete enum app definition
    * header Authorization = admin
    Given path '/gql-apps/test-enum'
    When method DELETE

Scenario: Delete union app definition
    * header Authorization = admin
    Given path '/gql-apps/test-union'
    When method DELETE

Scenario: Delete interface app definition
    * header Authorization = admin
    Given path '/gql-apps/test-union'
    When method DELETE

Scenario: Delete test data
    * header Authorization = admin
    Given path '/test-graphql/courses/*'
    And param filter = '{"_id":{"$exists":true}}'
    When method DELETE