Feature: Test field to aggregation mappings for GraphQL plugin

Background:
    * url graphQLBaseURL
    * path 'fta'

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

    * def setupData = callonce read('field-to-aggregation-setup.feature')

Scenario: Should return an array with two objects


    * text query =
    """
   {
     userByEmail(email: "foo@example.com"){
       name
       email
       postsByCategory
     }
   }
   """

   Given header Content-Type = contTypeGraphQL
   And header Authorization = admin
   And request query
   When method POST
   Then status 200
   And match $..postsByCategory.length() == 2

  
Scenario: Should return an empty array for emptyStage field
    * text query =
    """
      {
        userByEmail(email: "foo@example.com"){
          emptyStage
        }
      }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = admin
    And request query
    When method POST
    Then status 200
    And match $..emptyStage.length() == 0

Scenario: Should contain errors key
  * text query =
  """
    {
      userByEmail(email: "foo@example.com"){
        malformedStage
      }
    }
  """

  Given header Content-Type = contTypeGraphQL
  And header Authorization = admin
  And request query
  When method POST
  Then status 400
  And match response contains {"errors": "#present"} 