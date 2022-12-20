Feature: Test GraphQL union

Background:
    * url graphQLBaseURL
    * path 'test-union'

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

    * def setupData = callonce read('setup-test-data.feature')
    * def setupData = callonce read('setup-union.feature')

Scenario: query involving union should succeed

    * text query =
    """
    {
        AllCourses {
        ... on InternalCourse {
                title
            }
        ... on ExternalCourse {
                title
                deliveredBy
            }
        }
    }
   """

   Given header Content-Type = contTypeGraphQL
   And header Authorization = admin
   And request query
   When method POST
   Then status 200
   And match $.data.AllCourses.length() == 6