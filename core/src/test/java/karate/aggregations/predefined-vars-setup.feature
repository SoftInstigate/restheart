@ignore
Feature: Seed the fixture for predefined-vars

Background:
    * url baseUrl

Scenario: create the collection, its aggregations, its documents and the permission

    * header Authorization = adminAuth
    Given path '/test-aggr-vars'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # paged       — $skip and $limit from @skip and @limit, the page the client asks for
    # permitted   — $match on @mongoPermissions.readFilter, with {} as the default for a caller
    #               whose permission has no readFilter (the root role)
    * header Authorization = adminAuth
    Given path '/test-aggr-vars/docs'
    And request
    """
    {
      "aggrs": [
        {
          "uri": "paged",
          "stages": [
            { "$sort": { "n": 1 } },
            { "$skip": { "$var": "@skip" } },
            { "$limit": { "$var": "@limit" } },
            { "$project": { "_id": 0, "n": 1 } }
          ]
        },
        {
          "uri": "permitted",
          "stages": [
            { "$match": { "$var": ["@mongoPermissions.readFilter", {}] } },
            { "$sort": { "n": 1 } },
            { "$project": { "_id": 0, "n": 1 } }
          ]
        }
      ]
    }
    """
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # n 1..5: 1 and 2 are owned by aggrvars-user, 3 is public, 4 and 5 belong to someone else
    * def docs =
    """
    [
      { "_id": "d1", "n": 1, "owner": "aggrvars-user" },
      { "_id": "d2", "n": 2, "owner": "aggrvars-user" },
      { "_id": "d3", "n": 3, "owner": "someone", "public": true },
      { "_id": "d4", "n": 4, "owner": "someone" },
      { "_id": "d5", "n": 5, "owner": "someone" }
    ]
    """
    * header Authorization = adminAuth
    Given path '/test-aggr-vars/docs'
    And param wm = 'upsert'
    And request docs
    When method POST
    Then status 200

    # the readFilter: the caller's documents or the public ones; $or stored escaped, as in the ACL
    * header Authorization = adminAuth
    Given path '/restheart-test/acl/aggrVarsReadFilter'
    And param wm = 'upsert'
    And request { "roles": ["aggrvars-role"], "predicate": "path-prefix('/test-aggr-vars/docs') and method(GET)", "priority": 100, "mongo": { "readFilter": { "_$or": [ { "owner": "@user.userid" }, { "public": true } ] } } }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200
