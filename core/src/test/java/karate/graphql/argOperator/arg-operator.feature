Feature: Test rootDoc in query and aggregation mappings for GraphQL plugin

Background:
    * url graphQLBaseURL
    * path 'test-arg-operator'

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

    * def setupData = callonce read('setup.feature')

Scenario: Should return 1 Stuff

    * text query =
    """
    {
        stuff(options: { skip: 0, limit:1 }) {
            _id
        }
    }
    """

   Given header Content-Type = contTypeGraphQL
   And header Authorization = admin
   And request query
   When method POST
   Then status 200
   And match $.data.stuff.length() == 1

Scenario: Should return 2 Stuff

    * text query =
    """
    {
        stuff(options: { skip: 0, limit:2 }) {
            _id
        }
    }
    """

   Given header Content-Type = contTypeGraphQL
   And header Authorization = admin
   And request query
   When method POST
   Then status 200
   And match $.data.stuff.length() == 2