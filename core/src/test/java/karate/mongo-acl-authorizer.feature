Feature: test mongo-acl-authorizer

Background:
* url 'http://localhost:8080'
* def adminAuthHeader = 'Basic YWRtaW46c2VjcmV0'
* def wrongAuthHeader = 'Basic YWRtaW46d3Jvbmc='
* def fullAuthHeader = 'Basic ZnVsbDpzZWNyZXQ='
* def filteredAuthHeader = 'Basic ZmlsdGVyZWQ6c2VjcmV0'
* def bothAuthHeader = 'Basic Ym90aDpzZWNyZXQ='
* def users =
* def acl = read('maa-acl.json')
* def data = read('maa-data.json')
# credentials is admin:secret

Scenario: create test acl
    * header Authorization = adminAuthHeader
    Given path '/restheart-test/acl'
    And param wm = "upsert"
    And request read('maa-acl.json')
    When method POST
    Then status 200

Scenario: create test coll
    * header Authorization = adminAuthHeader
    Given path '/test-db'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = adminAuthHeader
    Given path '/test-db/test-authorization'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = adminAuthHeader
    Given path '/test-db/test-authorization/*'
    And param filter = '{"_id":{"$exists":true}}'
    When method DELETE
    Then status 200

Scenario: create test coll
    * header Authorization = adminAuthHeader
    Given path '/test-db/test-authorization'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

Scenario: create test coll
    * header Authorization = adminAuthHeader
    Given path '/test-db/test-authorization'
    And request read('maa-data.json')
    When method POST
    Then assert responseStatus == 201 || responseStatus == 200

Scenario: count test coll with admin user
    * header Authorization = adminAuthHeader
    Given path '/test-db/test-authorization/_size'
    When method GET
    Then status 200
    And match response._size == 4

Scenario: count test coll without authentication -> not authenticated
    Given path '/test-db/test-authorization/_size'
    When method GET
    Then status 401

Scenario: test coll without authentication -> get 3 docs because of readFilter on $unauthenticated role
    Given path '/test-db/test-authorization'
    And param rep = 's'
    When method GET
    Then status 200
    And match $.length() == 3

Scenario: test coll with filter user -> get 3 docs because of readFilter on priority predicate
    * header Authorization = filteredAuthHeader
    Given path '/test-db/test-authorization'
    And param rep = 's'
    When method GET
    Then status 200
    And match $.length() == 3

Scenario: test coll with filter user -> get 3 docs because of readFilter on priority predicate
    * header Authorization = filteredAuthHeader
    Given path '/test-db/test-authorization'
    And param rep = 's'
    When method GET
    Then status 200
    And match $.length() == 3

Scenario: test coll with full user -> get 4 docs because of no readFilter
    * header Authorization = fullAuthHeader
    Given path '/test-db/test-authorization'
    And param rep = 's'
    When method GET
    Then status 200
    And match $.length() == 4

Scenario: test coll with both user -> get 3 docs because of readFilter on priority predicate
    * header Authorization = filteredAuthHeader
    Given path '/test-db/test-authorization'
    And param rep = 's'
    When method GET
    Then status 200
    And match $.length() == 3

Scenario: write public doc with full user -> OK
    * header Authorization = fullAuthHeader
    Given path '/test-db/test-authorization'
    And request { "status": "public" }
    When method POST
    Then status 201

Scenario: write private doc with full user -> OK
    * header Authorization = fullAuthHeader
    Given path '/test-db/test-authorization'
    And request { "status": "private" }
    When method POST
    Then status 201

Scenario: write public doc with filtered user -> OK
    * header Authorization = filteredAuthHeader
    Given path '/test-db/test-authorization'
    And request { "status": "public" }
    When method POST
    Then status 201

Scenario: filtered creates a doc with status=private -> OK, than updates it -> FAILS
    * header Authorization = filteredAuthHeader
    Given path '/test-db/test-authorization'
    And request { "_id": "private", "status": "private" }
    When method POST
    Then status 201

    * header Authorization = filteredAuthHeader
    Given path '/test-db/test-authorization/private'
    And param wm = "upsert"
    And request { "status": "public" }
    When method PATCH
    Then status 409

    * header Authorization = fullAuthHeader
    Given path '/test-db/test-authorization/private'
    And param rep = 's'
    When method GET
    Then status 200
    And match $.status == 'private'

Scenario: filtered creates few docs-> OK, than updates it -> only public ones are updated
    * header Authorization = filteredAuthHeader
    Given path '/test-db/test-authorization'
    And request [{ "_id":"fd1", "status": "private", "selector": "xyz" }, { "_id":"fd2", "status": "private", "selector": "xyz" },  { "_id":"fd3", "status": "public", "selector": "xyz" }]
    When method POST
    Then status 200

    * header Authorization = filteredAuthHeader
    Given path '/test-db/test-authorization'
    And param rep = 's'
    And param wm = "upsert"
    And request [{ "_id":"fd1", "status": "private", "selector": "xyz", "updated1": true }, { "_id":"fd2", "status": "private", "selector": "xyz", "updated1": true },  { "_id":"fd3", "status": "public", "selector": "xyz", "updated1": true }]
    When method POST
    Then status 207
    And match $.inserted == 0
    And match $.deleted == 0
    And match $.modified == 1
    And match $.matched == 1

    * header Authorization = fullAuthHeader
    Given path '/test-db/test-authorization/_size'
    And param filter = '{"$and": [{"selector":"xyz"}, {"updated1":true}]}'
    When method GET
    Then status 200
    And match $._size == 1

    * header Authorization = filteredAuthHeader
    Given path '/test-db/test-authorization/*'
    And param filter = '{"selector":"xyz"}'
    And param rep = 's'
    And request { "updated2": true }
    When method PATCH
    Then status 200
    And match $.inserted == 0
    And match $.deleted == 0
    And match $.modified == 1
    And match $.matched == 1

    * header Authorization = fullAuthHeader
    Given path '/test-db/test-authorization/_size'
    And param filter = '{"$and": [{"selector":"xyz"}, {"updated2":true}]}'
    And param rep = 's'
    When method GET
    Then status 200
    And match $._size == 1