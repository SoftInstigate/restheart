@ignore
Feature: Setup test data for enum, union and interface

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


Scenario: Create test data

    # crete test-graphql db
    * header Authorization = admin
    Given path '/test-graphql'
    When method PUT

    # crete courses collection
    * header Authorization = admin
    Given path '/test-graphql/courses'
    When method PUT

    # insert courses
    * header Authorization = admin
    Given path '/test-graphql/courses'
    And request read('test-data.json')
    When method POST