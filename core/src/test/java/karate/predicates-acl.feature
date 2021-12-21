Feature: Test predicates in acl

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
  * def test = basic({username: 'test', password: 'secret' })
  * def testWithArray = basic({username: 'testWithArray', password: 'secret'})
  * def testWithEmptyArray = basic({username: 'testWithEmptyArray', password: 'secret'})
  * def testWithNotExistingArray = basic({username: 'testWithNotExistingArray', password: 'secret'})
  * def admin = basic({username: 'admin', password: 'secret'})

Scenario: Test qparams-size(2)

  * header Authorization = test
  Given path '/secho'
  * params { filter: "param2", page: 2 }
  When method GET
  Then status 200

  # must fail, qparams size != 2
  * header Authorization = test
  Given path '/secho'
  * params { filter: "param1" }
  When method GET
  Then status 403

Scenario: Test qparams-blacklist(size)

  * header Authorization = test 
  Given path '/secho'
  * params { filter: "param1", q1: "param2" }
  When method GET
  Then status 200


  * header Authorization = test 
  Given path '/secho'
  * params { filter: "param1", size: "param2" }
  When method GET
  Then status 403

Scenario: Test qparams-whitelist(filter)

  * header Authorization = test 
  Given path '/secho'
  * params { filter: "param1", q1: "2" }
  When method GET
  Then status 200

# allowed keys are "title" and "content". Every request must contain "title" key
Scenario: Test bson-request-contains and bson-request-whitelist

  * def setupData = call read('predicate-acl-setup.feature')

  # valid request
  * header Authorization = test
  Given path 'test-predicates/coll'
  And request { title: "Valid predicate" }
  When method POST
  Then status 201

  # valid request
  * header Authorization = test
  Given path 'test-predicates/coll'
  And request { title: "predicates", content: "Testing valid request" }
  When method POST
  Then status 201

  # "example" keys is now allowed > 403
  * header Authorization = test
  Given path 'test-predicates/coll'
  And request { example: "Hello" }
  When method POST
  Then status 403

  # "example" and "test" keys are now allowed > 403
  * header Authorization = test
  Given path 'test-predicates/coll'
  And request { example: "Hello", test: "test value" }
  When method POST
  Then status 403

  And call read('predicate-acl-cleanup.feature') { ETag: '#(setupData.ETag)' }


# every post request will get merged with {"author": "@user.userid", "status": "draft", "log": "@request"}
Scenario: Test mongodb mergeRequest

  * def setupData = call read('predicate-acl-setup.feature')

  # create a document
  * headers { Authorization: '#(test)'}
  Given path 'test-predicates/coll'
  And request { title: 'Test mergeRequest' }
  When method POST
  Then status 201


  # check inserted document
  * headers { Authorization: '#(test)'}
  Given url responseHeaders['Location'][0]
  When method GET
  Then status 200
  And match response contains {log: '#notnull', author: 'test', status: 'draft', title: 'Test mergeRequest'}


  And call read('predicate-acl-cleanup.feature') { ETag: '#(setupData.ETag)' }


Scenario: Test mongodb projectResponse

  * def setupData = call read('predicate-acl-setup.feature')

  # create a document in coll collection
  * headers { Authorization: '#(test)'}
  Given path 'test-predicates/coll'
  And request { title: 'Test projectResponse' }
  When method POST
  Then status 201


  * headers { Authorization: '#(test)'}
  And path '/test-predicates/coll'
  * params {rep: 's'}
  When method GET
  Then status 200
  And match response[0] contains {log: '#notpresent'}


  # create a document in projectResponse collection
  * headers { Authorization: '#(test)'}
  Given path 'test-predicates/projectResponse'
  And request { title: 'Test projectResponse' }
  When method POST
  Then status 201

  # check that response contains ONLY user key
  * headers { Authorization: '#(test)'}
  And path '/test-predicates/projectResponse'
  * params {rep: 's'}
  When method GET
  Then status 200
  And match response[0] contains only {user: '#notnull'}


  And call read('predicate-acl-cleanup.feature') { ETag: '#(setupData.ETag)' }


Scenario: Test in() predicate

    * def setupData = call read('predicate-acl-setup.feature')

    * header Authorization = testWithArray
    Given path '/test-predicates/coll/one'
    And param wm = "upsert"
    When method PUT
    Then status 201

    * header Authorization = testWithArray
    Given path '/test-predicates/coll/two'
    And param wm = "upsert"
    When method PUT
    Then status 201

    * header Authorization = testWithArray
    Given path '/test-predicates/coll/three'
    And param wm = "upsert"
    When method PUT
    Then status 201

    * header Authorization = testWithArray
    Given path '/test-predicates/coll/four'
    And param wm = "upsert"
    When method PUT
    Then status 403

    * header Authorization = testWithArray
    Given path '/test-predicates/coll/one'
    When method GET
    Then status 200

    * header Authorization = testWithArray
    Given path '/test-predicates/coll/two'
    When method GET
    Then status 200

    * header Authorization = testWithArray
    Given path '/test-predicates/coll/three'
    When method GET
    Then status 200

    * header Authorization = testWithArray
    Given path '/test-predicates/coll/four'
    When method GET
    Then status 403

    * header Authorization = testWithEmptyArray
    Given path '/test-predicates/coll/one'
    When method GET
    Then status 403

    * header Authorization = testWithNotExistingArray
    Given path '/test-predicates/coll/one'
    When method GET
    Then status 403

    * header Authorization = testWithNotExistingArray
    Given path '/test-predicates/coll/@user.array'
    When method GET
    Then status 403

    * header Authorization = testWithNotExistingArray
    Given path '/test-predicates/coll/null'
    When method GET
    Then status 403

    And call read('predicate-acl-cleanup.feature') { ETag: '#(setupData.ETag)' }
