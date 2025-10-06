Feature: Test Aggregation Pipeline Security

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
  * def admin = basic({username: 'admin', password: 'secret'})
  * def setupData = callonce read('setup.feature')

Scenario: Safe aggregation should work normally
  Given path '/test-aggr-security/coll/_aggrs/safe-aggr'
  And header Authorization = admin
  When method GET
  Then status 200
  And match $._returned == 1
  And match $._embedded.rh:result[0]._id == "A"
  And match $._embedded.rh:result[0].total == 250

Scenario: Aggregation with $out stage should be blocked by security
  Given path '/test-aggr-security/coll/_aggrs/unsafe-out'
  And header Authorization = admin
  When method GET
  Then status 403
  And match $.message contains "aggregation pipeline security violation"
  And match $.message contains "BLACKLISTED_STAGE"
  And match $.message contains "$out"

Scenario: Aggregation with cross-database $lookup should be blocked
  Given path '/test-aggr-security/coll/_aggrs/unsafe-lookup'
  And header Authorization = admin
  When method GET
  Then status 403
  And match $.message contains "aggregation pipeline security violation"
  And match $.message contains "CROSS_DATABASE_ACCESS"
  And match $.message contains "$lookup"

Scenario: Aggregation with $where operator should be blocked
  Given path '/test-aggr-security/coll/_aggrs/unsafe-where'
  And header Authorization = admin
  When method GET
  Then status 403
  And match $.message contains "aggregation pipeline security violation"
  And match $.message contains "BLACKLISTED_OPERATOR"
  And match $.message contains "$where"

Scenario: Aggregation with $function operator should be blocked
  Given path '/test-aggr-security/coll/_aggrs/unsafe-function'
  And header Authorization = admin
  When method GET
  Then status 403
  And match $.message contains "aggregation pipeline security violation"
  And match $.message contains "BLACKLISTED_OPERATOR"
  And match $.message contains "$function"

Scenario: Test inline aggregation with blocked stage
  Given path '/test-aggr-security/coll'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And param aggrs = '[{"$match": {"category": "A"}}, {"$merge": {"into": "evil-collection"}}]'
  When method GET
  Then status 403
  And match $.message contains "aggregation pipeline security violation"
  And match $.message contains "BLACKLISTED_STAGE"
  And match $.message contains "$merge"

Scenario: Test inline aggregation with allowed stages
  Given path '/test-aggr-security/coll'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And param aggrs = '[{"$match": {"category": "A"}}, {"$sort": {"value": -1}}]'
  When method GET
  Then status 200
  And match $._returned == 2
  And match $._embedded.rh:doc[0].name == "doc3"
  And match $._embedded.rh:doc[1].name == "doc1"