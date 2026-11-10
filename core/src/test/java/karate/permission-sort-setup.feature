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
