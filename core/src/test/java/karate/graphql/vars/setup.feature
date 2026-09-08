@ignore
Feature: Setup for the @ variables available to a GraphQL mapping

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

Scenario: Create the app definition and the data it reads

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
    Given path '/test-graphql/gql-apps/test-vars'
    And param wm = "upsert"
    And request read('app-definition.json')
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # the collection carries an aggregation scoped by @user, so the same variable can be
    # exercised from the REST side too — that is where ?avars lets a caller try to supply one
    * header Authorization = admin
    Given path '/test-graphql/test-vars'
    And request
    """
    {
      "aggrs": [
        {
          "uri": "mine",
          "stages": [ { "$match": { "owner": { "$var": "@user.userid" } } } ]
        }
      ]
    }
    """
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # the documents carry fixed _ids and are written with wm=upsert, so a re-run lands on
    # exactly the same four rather than accumulating
    * header Authorization = admin
    Given path '/test-graphql/test-vars'
    And param wm = "upsert"
    And request read('data.json')
    When method POST
    Then assert responseStatus == 200 || responseStatus == 201
