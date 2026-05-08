Feature: cache consistency with transactions

Background:
* url 'http://localhost:8080'
* def db = '/test-caching-txns'
* def coll = '/test-caching-txns/coll'
* def authHeader = 'Basic YWRtaW46c2VjcmV0'
* def sidFromLocation = function(location) { return location.substring(location.length-36); }

@requires-mongodb-4 @requires-replica-set
Scenario: Setup test data
    * header Authorization = authHeader
    Given path db
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path coll
    And request { }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = authHeader
    Given path coll
    And request
    """
    [
      { "_id": "1", "k": "a" },
      { "_id": "2", "k": "b" }
    ]
    """
    When method POST
    Then status 200

@requires-mongodb-4 @requires-replica-set
Scenario: cached collection reads inside a transaction reflect in-transaction writes
    * header Authorization = authHeader
    Given path '/_sessions'
    And request { }
    When method POST
    Then status 201
    And match header Location contains '/_sessions/'
    * def sid = sidFromLocation(responseHeaders['Location'][0])

    * header Authorization = authHeader
    Given path '/_sessions/' + sid + '/_txns'
    And request { }
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path coll
    And param sid = sid
    And param txn = 1
    And param cache = ''
    And param count = ''
    And param page = 1
    And param pagesize = 10
    And param sort_by = '_id'
    When method GET
    Then status 200
    And match response._returned == 2
    And match response._size == 2

    * header Authorization = authHeader
    Given path coll
    And param sid = sid
    And param txn = 1
    And request { "_id": "tx-new", "k": "txn" }
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path coll
    And param sid = sid
    And param txn = 1
    And param cache = ''
    And param count = ''
    And param page = 1
    And param pagesize = 10
    And param sort_by = '_id'
    When method GET
    Then status 200
    And match response._returned == 3
    And match response._size == 3

    * header Authorization = authHeader
    Given path coll + '/tx-new'
    When method GET
    Then status 404

    * header Authorization = authHeader
    Given path '/_sessions/' + sid + '/_txns/1'
    When method DELETE
    Then status 204

    * header Authorization = authHeader
    Given path coll + '/tx-new'
    When method GET
    Then status 404

@requires-mongodb-4 @requires-replica-set
Scenario: Cleanup
    * header Authorization = authHeader
    Given path db
    When method GET
    Then status 200
    * def etag = responseHeaders['ETag'][0]

    * headers { Authorization: '#(authHeader)', 'If-Match': '#(etag)' }
    Given path db
    When method DELETE
    Then status 204