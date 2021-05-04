@onlyme
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


Scenario: Test bson-request-contains, request must contain specified keys in acl

  * def setupData = call read('predicate-acl-setup.feature')

  * print "After setup data"

  * print setupData.ETag

  * print "last print"

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

