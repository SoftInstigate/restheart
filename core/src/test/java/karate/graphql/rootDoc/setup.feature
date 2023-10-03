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
    Given path '/test-graphql/gql-apps/test-rootDoc'
    And param wm = "upsert"
    And request read('app-definition-for-rootDoc.json')
    When method PUT

    # crete authors-and-posts collection
    * header Authorization = admin
    Given path '/test-graphql/authors-and-posts'
    When method PUT

    # insert authors and posts
    * header Authorization = admin
    Given path '/test-graphql/authors-and-posts'
    And request read('authors-and-posts.json')
    When method POST

    # crete groups collection
    * header Authorization = admin
    Given path '/test-graphql/groups'
    When method PUT

    # insert groups
    * header Authorization = admin
    Given path '/test-graphql/groups'
    And request read('groups.json')
    When method POST