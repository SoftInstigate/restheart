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

    * def req = 
    """
    {
        "query":"query exampleOperation($email: String!){userByEmail(email: $email){ name email postsByCategory }}",
        "variables":{
            "email": "foo@example.com"
        }
    }   
    """

    * def setupData = callonce read('field-to-aggregation-setup.feature')

Scenario: Aggregation Pipeline with two stages


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
