@ignore
Feature: Cleaup mock data for field to aggregation mapping

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


Scenario: Delete mock definition from gql-app collection
    
    # detele document for field-to-aggregation mapping test
    * header Authorization = admin
    Given path '/gql-apps/field-to-aggregation'
    When method DELETE
    Then status 204


    # delete users-fta collection



    # delete posts-fta collection