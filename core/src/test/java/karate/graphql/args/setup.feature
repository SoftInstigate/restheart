@ignore
Feature: Setup for the GraphQL argument checks

  Four documents with fixed _ids, written with wm=upsert, so a re-run lands on the same four.

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

Scenario: Create the app definition

    * header Authorization = admin
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = admin
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = admin
    Given path '/test-graphql/test-args'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = admin
    Given path '/test-graphql/test-args'
    And param wm = "upsert"
    And request [ { _id: 'a1', name: 'alpha', owner: 'aclowner1' }, { _id: 'a2', name: 'bravo', owner: 'aclowner1' }, { _id: 'b1', name: 'charlie', owner: 'aclowner2' }, { _id: 'f1', name: 'future', owner: 'aclowner2' } ]
    When method POST
    Then assert responseStatus == 200

    * header Authorization = admin
    Given path '/test-graphql/gql-apps/test-args'
    And param wm = "upsert"
    And request read('app-definition.json')
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200
