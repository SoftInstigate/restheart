@ignore
Feature: Seed the fixture for allow-filter

Background:
    * url baseUrl

Scenario: create the collections and the permissions

    * header Authorization = adminAuth
    Given path '/test-allow-filter'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # denied: allowFilter false, with an aggregation to query through
    * header Authorization = adminAuth
    Given path '/test-allow-filter/denied'
    And request { "aggrs": [ { "uri": "byStatus", "stages": [ { "$match": { "status": { "$var": "status" } } } ] } ] }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = adminAuth
    Given path '/test-allow-filter/allowed'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = adminAuth
    Given path '/test-allow-filter/unset'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * def coll = ['denied', 'allowed', 'unset']
    * def seed = function(c) { karate.call('classpath:karate/allow-filter-seed-doc.feature', { coll: c }) }
    * karate.forEach(coll, seed)

    # allow-filter-user, with the role allow-filter-role, is a user of fileRealmAuthenticator in
    # conf-overrides.yml

    * header Authorization = adminAuth
    Given path '/restheart-test/acl/allowFilterDenied'
    And param wm = 'upsert'
    And request { "roles": ["allow-filter-role"], "predicate": "path-prefix('/test-allow-filter/denied') and method(GET)", "priority": 100, "mongo": { "allowFilter": false } }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = adminAuth
    Given path '/restheart-test/acl/allowFilterAllowed'
    And param wm = 'upsert'
    And request { "roles": ["allow-filter-role"], "predicate": "path-prefix('/test-allow-filter/allowed') and method(GET)", "priority": 100, "mongo": { "allowFilter": true } }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = adminAuth
    Given path '/restheart-test/acl/allowFilterUnset'
    And param wm = 'upsert'
    And request { "roles": ["allow-filter-role"], "predicate": "path-prefix('/test-allow-filter/unset') and method(GET)", "priority": 100 }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200
