@ignore
Feature: Seed the fixture for acl-filter-unbound-var

Background:
    * url 'http://localhost:8080'
    * def admin = 'Basic YWRtaW46c2VjcmV0'


Scenario: create the collection and its documents

    * header Authorization = admin
    Given path '/test-acl-unbound'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = admin
    Given path '/test-acl-unbound/docs'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = admin
    Given path '/test-acl-unbound/docs/*'
    And param filter = '{"_id":{"$exists":true}}'
    When method DELETE
    Then status 200

    # two documents without an owner, which {"owner": null} would match, one owned by
    # someone else and one public
    * header Authorization = admin
    Given path '/test-acl-unbound/docs'
    And request [{ "name": "unowned-1" }, { "name": "unowned-2", "owner": null }, { "name": "other", "owner": "someone" }, { "name": "public", "public": true }]
    When method POST
    Then assert responseStatus == 200 || responseStatus == 201

    * header Authorization = admin
    Given path '/test-acl-unbound/stamped'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200
