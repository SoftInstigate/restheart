@ignore
Feature: Setup mock data for field to aggreagtion feature test

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
    Given path '/test-graphql/gql-apps/fta'
    And param wm = "upsert"
    And request read('app-definition-field-to-aggregation.json')
    When method PUT



    # crete user-fta collection
    * header Authorization = admin
    Given path '/test-graphql/users-fta'
    When method PUT



    # create post-fta collection
    * header Authorization = admin
    Given path '/test-graphql/posts-fta'
    When method PUT



    # insert a user
    * header Authorization = admin
    Given path '/test-graphql/users-fta'
    And request read('fta-user.json')
    When method POST



    # insert some posts for that user
    * header Authorization = admin
    Given path '/test-graphql/posts-fta'
    And request read('fta-posts.json')
    When method POST