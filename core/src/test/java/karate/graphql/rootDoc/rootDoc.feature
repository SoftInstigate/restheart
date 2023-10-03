Feature: Test rootDoc in query and aggregation mappings for GraphQL plugin

Background:
    * url graphQLBaseURL
    * path 'test-rootDoc'

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

Scenario: Should return two posts

    * text query =
    """
   {
     authors{
       _id
       group {
        description
       }
       posts(visible: true) {
        content
       }
     }
   }
   """

   Given header Content-Type = contTypeGraphQL
   And header Authorization = admin
   And request query
   When method POST
   Then status 200
   And match $..posts.length() == 2
   And match $.data.authors[0].group.description == 'the coolest authors'

Scenario: Should return one post

    * text query =
    """
   {
     authors{
       _id
       group {
        description
       }
       posts(visible: false) {
        content
       }
     }
   }
   """

   Given header Content-Type = contTypeGraphQL
   And header Authorization = admin
   And request query
   When method POST
   Then status 200
   And match $..posts.length() == 1
   And match $.data.authors[0].group.description == 'the coolest authors'
