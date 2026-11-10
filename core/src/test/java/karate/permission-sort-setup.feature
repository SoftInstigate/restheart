@ignore
Feature: Seed the fixture for permission-sort

Background:
    * url baseUrl

Scenario: create the collection, its documents and the permission

    * header Authorization = adminAuth
    Given path '/test-permission-sort'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = adminAuth
    Given path '/test-permission-sort/products'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * def products =
    """
    [
      { "_id": "a", "name": "Cup", "category": "kitchen", "published": true, "n": 1 },
      { "_id": "b", "name": "Apron", "category": "kitchen", "published": true, "n": 2 },
      { "_id": "c", "name": "Desk", "category": "office", "published": true, "n": 3 },
      { "_id": "d", "name": "Draft", "category": "office", "published": false, "n": 4 }
    ]
    """
    * header Authorization = adminAuth
    Given path '/test-permission-sort/products'
    And param wm = 'upsert'
    And request products
    When method POST
    Then assert responseStatus == 200 || responseStatus == 201

    # plain: the same documents, under a permission that names query parameters without @
    * header Authorization = adminAuth
    Given path '/test-permission-sort/plain'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = adminAuth
    Given path '/test-permission-sort/plain'
    And param wm = 'upsert'
    And request products
    When method POST
    Then assert responseStatus == 200 || responseStatus == 201

    * header Authorization = adminAuth
    Given path '/test-permission-sort/broken'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # allow-filter-user, with the role allow-filter-role, is a user of fileRealmAuthenticator in
    # conf-overrides.yml: the permission declares a mandatory condition, an optional one the client
    # picks with ?category=, two named orders the client picks with ?order=, and a default order
    * header Authorization = adminAuth
    Given path '/restheart-test/acl/permissionSortProducts'
    And param wm = 'upsert'
    And request
    """
    {
      "roles": ["allow-filter-role"],
      "predicate": "path-prefix('/test-permission-sort/products') and method(GET)",
      "priority": 100,
      "mongo": {
        "allowFilter": false,
        "readFilter": {
          "$and": [
            { "published": true },
            { "$ifvar": ["@qparams['category']", { "category": "@qparams['category']" }] }
          ]
        },
        "sort": {
          "$ifvar": ["@qparams['order']",
            { "by-name": { "name": 1 }, "newest": { "n": -1 } },
            { "n": -1 }
          ]
        }
      }
    }
    """
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = adminAuth
    Given path '/restheart-test/acl/permissionSortPlain'
    And param wm = 'upsert'
    And request
    """
    {
      "roles": ["allow-filter-role"],
      "predicate": "path-prefix('/test-permission-sort/plain') and method(GET)",
      "priority": 100,
      "mongo": {
        "readFilter": { "category": { "$var": ["cat", "kitchen"] } },
        "sort": { "n": { "$var": ["dir", -1] } }
      }
    }
    """
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # broken: valid when written, then made invalid by a PATCH in dot notation, which the check on
    # write does not read; a request then finds it invalid and is denied
    * header Authorization = adminAuth
    Given path '/restheart-test/acl/permissionSortBroken'
    And param wm = 'upsert'
    And request { "roles": ["allow-filter-role"], "predicate": "path-prefix('/test-permission-sort/broken') and method(GET)", "priority": 100, "mongo": { "sort": { "n": 1 } } }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = adminAuth
    Given path '/restheart-test/acl/permissionSortBroken'
    And request { "$set": { "mongo.sort": { "n": { "$var": [1] } } } }
    When method PATCH
    Then assert responseStatus == 200
