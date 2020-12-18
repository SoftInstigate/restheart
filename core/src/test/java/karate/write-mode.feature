Feature: test basic authentication mechanism

Background:
* url 'http://localhost:8080'
* def basic =
"""
function(creds) {
  var temp = creds.username + ':' + creds.password;
  var Base64 = Java.type('java.util.Base64');
  var encoded = Base64.getEncoder().encodeToString(temp.bytes);
  return 'Basic ' + encoded;
}
"""
* def authHeader = basic({username: 'admin', password: 'secret' })
* def idFromlocation = function(url) { return url.substring(url.length-24); }

Scenario: Create test db and collection
    * header Authorization = authHeader
    Given path '/test-write-mode'
    And request {}
    When method PUT
    Then status 201

    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request {}
    When method PUT
    Then status 201

# **************** INSERT ****************

Scenario: POST with wm=insert and _id
    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request { _id: "insert", a: 1 }
    And param wm = "insert"
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/insert'
    When method GET
    Then status 200
    And match response.a == 1

    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request { _id: "insert", a: 1 }
    And param wm = "insert"
    When method POST
    Then status 409

  Scenario: POST with wm=insert without _id
    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request { a: 1 }
    And param wm = "insert"
    When method POST
    Then status 201
    * def newid = idFromlocation(responseHeaders['Location'][0])

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/' + newid
    When method GET
    Then status 200
    And match response._id['$oid'] == newid

    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request { _id: { '$oid': '#(newid)' }}, a: 1 }
    And param wm = "insert"
    When method POST
    Then status 409

  Scenario: PUT with wm=insert
    * header Authorization = authHeader
    Given path '/test-write-mode/coll/insert2'
    And request { _id: 'insert2', a: 1 }
    And param wm = "insert"
    When method PUT
    Then status 201

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/insert2'
    When method GET
    Then status 200
    And match response.a == 1

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/insert2'
    And request { _id: "insert2", a: 1 }
    And param wm = "insert"
    When method PUT
    Then status 409

  Scenario: PATCH with wm=insert
    * header Authorization = authHeader
    Given path '/test-write-mode/coll/insert3'
    And request { _id: 'insert3', a: 1 }
    And param wm = "insert"
    When method PATCH
    Then status 201

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/insert3'
    When method GET
    Then status 200
    And match response.a == 1

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/insert3'
    And request { _id: "insert3", a: 1 }
    And param wm = "insert"
    When method PATCH
    Then status 409

# **************** BULK INSERT ****************

Scenario: bulk POST with wm=insert and _id, standard representation
    * header Authorization = authHeader
    And param rep = 's'
    Given path '/test-write-mode/coll'
    And request [{ _id: "bi1", a: 1 },{ _id: "bi2", a: 1 },{ _id: "bi3", a: 1 }]
    And param wm = "insert"
    When method POST
    Then status 200
    And match response.inserted == 3
    And match response.deleted == 0
    And match response.modified == 0
    And match response.matched == 0

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/bi1'
    When method GET
    Then status 200
    And match response.a == 1

    * header Authorization = authHeader
    And param rep = 's'
    Given path '/test-write-mode/coll'
    And request [{ _id: "bi1", a: 1 },{ _id: "bi2", a: 1 },{ _id: "bi3", a: 1 }]
    And param wm = "insert"
    When method POST
    Then status 207
    And match response.inserted == 0
    And match response.deleted == 0
    And match response.modified == 0
    And match response.matched == 0
    And match response.errors[0].httpStatus == 409

Scenario: bulk POST with wm=update, standard representation

    * header Authorization = authHeader
    And param rep = 's'
    Given path '/test-write-mode/coll'
    And request [{ _id: "bu1", a: 2 },{ _id: "bu2", a: 2 },{ _id: "bu3", a: 2 }]
    And param wm = "update"
    When method POST
    Then status 200
    And match response.inserted == 0
    And match response.deleted == 0
    And match response.modified == 0
    And match response.matched == 0

    * header Authorization = authHeader
    And param rep = 's'
    Given path '/test-write-mode/coll'
    And request [{ _id: "bu1", a: 1 },{ _id: "bu2", a: 1 },{ _id: "bu3", a: 1 }]
    And param wm = "insert"
    When method POST
    Then status 200
    And match response.inserted == 3
    And match response.deleted == 0
    And match response.modified == 0
    And match response.matched == 0

    * header Authorization = authHeader
    And param rep = 's'
    Given path '/test-write-mode/coll'
    And request [{ _id: "bu1", a: 2 },{ _id: "bu2", a: 2 },{ _id: "bu3", a: 2 }]
    And param wm = "update"
    When method POST
    Then status 200
    And match response.inserted == 0
    And match response.deleted == 0
    And match response.modified == 3
    And match response.matched == 3

Scenario: bulk POST with wm=insert and _id, HAL representation
    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And param rep = 'hal'
    And request [{ _id: "bihal1", a: 1 },{ _id: "bihal2", a: 1 },{ _id: "bihal3", a: 1 }]
    And param wm = "insert"
    When method POST
    Then status 200
    And match response._embedded['rh:result'][0].inserted == 3
    And match response._embedded['rh:result'][0].deleted == 0
    And match response._embedded['rh:result'][0].modified == 0
    And match response._embedded['rh:result'][0].matched == 0

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/bihal1'
    When method GET
    Then status 200
    And match response.a == 1

    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And param rep = 'hal'
    And request [{ _id: "bihal1", a: 1 },{ _id: "bihal2", a: 1 },{ _id: "bihal3", a: 1 }]
    And param wm = "insert"
    When method POST
    Then status 207
    # for errors the response is not HAL, known limitation of legacy HAL format
    And match response.inserted == 0
    And match response.deleted == 0
    And match response.modified == 0
    And match response.matched == 0
    And match response.errors[0].httpStatus == 409

# **************** UPDATE ****************

Scenario: POST with wm=update and _id
    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request { _id: "update", a: 1 }
    And param wm = "update"
    When method POST
    Then status 404

    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request { _id: "update", a: 1 }
    And param wm = "insert"
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update'
    When method GET
    Then status 200
    And match response.a == 1

    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request { _id: "update", a: 2 }
    And param wm = "update"
    When method POST
    Then status 200

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update'
    When method GET
    Then status 200
    And match response.a == 2

  Scenario: POST with wm=update without _id
    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request { a: 1 }
    And param wm = "update"
    When method POST
    Then status 400

    Scenario: POST with wm=update with _id
    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request { _id: 'update2', a: 1 }
    And param wm = "update"
    When method POST
    Then status 404

    Scenario: POST with wm=insert with _id
    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request { _id: 'update2', a: 1 }
    And param wm = "insert"
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update2'
    When method GET
    Then status 200
    And match response.a == 1

    * header Authorization = authHeader
    Given path '/test-write-mode/coll'
    And request { _id: 'update2', a: 2 }
    And param wm = "update"
    When method POST
    Then status 200

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update2'
    When method GET
    Then status 200
    And match response._id == 'update2'
    And match response.a == 2

  Scenario: PUT with wm=update
    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update3'
    And request { _id: 'update3', a: 1 }
    And param wm = "update"
    When method PUT
    Then status 404

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update3'
    And request { _id: "update3", a: 1 }
    And param wm = "insert"
    When method PUT
    Then status 201

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update3'
    When method GET
    Then status 200
    And match response.a == 1

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update3'
    And request { _id: 'update3', a: 2 }
    And param wm = "update"
    When method PUT
    Then status 200

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update3'
    When method GET
    Then status 200
    And match response.a == 2

  Scenario: PATCH with wm=update
    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update4'
    And request { a: 1 }
    And param wm = "update"
    When method PATCH
    Then status 404

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update4'
    And request { a: 1 }
    And param wm = "insert"
    When method PATCH
    Then status 201

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update4'
    When method GET
    Then status 200
    And match response.a == 1

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update4'
    And request { a: 2 }
    And param wm = "update"
    When method PATCH
    Then status 200

    * header Authorization = authHeader
    Given path '/test-write-mode/coll/update4'
    When method GET
    Then status 200
    And match response.a == 2