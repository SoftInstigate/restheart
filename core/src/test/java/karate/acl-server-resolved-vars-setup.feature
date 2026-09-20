@ignore
Feature: Seed the fixture for acl-server-resolved-vars

Background:
    * url 'http://localhost:8080'
    * def admin = 'Basic YWRtaW46c2VjcmV0'


Scenario: create the collection, its aggregation and its documents

    * header Authorization = admin
    Given path '/test-server-vars'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # the pipeline scopes itself by the caller's own identity, resolved server-side
    * header Authorization = admin
    Given path '/test-server-vars/docs'
    And param wm = 'upsert'
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

    * header Authorization = admin
    Given path '/test-server-vars/docs/*'
    And param filter = '{"_id":{"$exists":true}}'
    When method DELETE
    Then status 200

    * header Authorization = admin
    Given path '/test-server-vars/docs'
    And request [{ "name": "admins-own", "owner": "admin" }, { "name": "somebody-elses", "owner": "someone-else" }]
    When method POST
    Then assert responseStatus == 200 || responseStatus == 201
