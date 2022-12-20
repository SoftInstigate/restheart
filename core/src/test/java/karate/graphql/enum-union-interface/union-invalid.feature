Feature: Test invalid mappings for GraphQL interface

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

    * def setupData = callonce read('setup-test-data.feature')
    * def setupData = callonce read('setup-union.feature')


Scenario: mapping missing $typeResolver should fail
    * def content =
    """
    { "mappings":
        {
            "Course": { }
        }
    }
    """
    Given path '/test-graphql/gql-apps/test-union'
    And header Authorization = admin
    And request content
    When method PATCH
    Then status 400
    And match $.message == "Wrong GraphQL App definition: Missing $typeResolver for union Course"

Scenario: incomplete mapping should fail
    * def content =
    """
    {
        "mappings": {
            "Course": {
                "$typeResolver": {
                    "InternalCourse": "not field-exists(sub.external)"
                }
            }
        }
    }
    """
    Given path '/test-graphql/gql-apps/test-union'
    And header Authorization = admin
    And request content
    When method PATCH
    Then status 400
    And match $.message == "Wrong GraphQL App definition: $typeResolver for union Course does not map all union members"

Scenario: mapping with invalid predicate
    * def content =
    """
    {
        "mappings": {
            "Course": {
                "$typeResolver": {
                    "InternalCourse": "foo(bar) and not field-exists(external)",
                    "ExternalCourse": "field-eq(field=external, value=true)"
                }
            }
        }
    }
    """
    Given path '/test-graphql/gql-apps/test-union'
    And header Authorization = admin
    And request content
    When method PATCH
    Then status 400
    And match $.message == "Wrong GraphQL App definition: error parsing $typeResolver predicate: foo(bar) and not field-exists(external)"