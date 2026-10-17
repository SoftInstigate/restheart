# The operators that run JavaScript on the MongoDB server ($where, $function, $accumulator) are
# refused with 403 wherever a client can write them: the filter and keys query parameters, checked
# by filterOperatorsBlacklist, and the aggregation pipeline of a PATCH request, checked by
# updatePipelineSecurityChecker against the aggregationSecurity blacklists. Both run with the
# default configuration.
Feature: Operators that run JavaScript on the server are refused wherever a client can write them

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
  * def db = 'test-operators-blacklist'
  # a harmless function: the point is that it never reaches MongoDB
  * def fn = '{"$function": {"body": "function() { return 1; }", "args": [], "lang": "js"}}'

  Given path db
  And header Authorization = admin
  When method PUT
  Then assert responseStatus == 201 || responseStatus == 200

  Given path db, 'coll'
  And header Authorization = admin
  When method PUT
  Then assert responseStatus == 201 || responseStatus == 200

  # a PUT on a new document needs the upsert write mode
  Given path db, 'coll', 'doc'
  And param wm = 'upsert'
  And header Authorization = admin
  And request { "a": 1 }
  When method PUT
  Then assert responseStatus == 201 || responseStatus == 200

Scenario: a plain filter is served
  Given path db, 'coll'
  And param filter = '{"a": 1}'
  And header Authorization = admin
  When method GET
  Then status 200
  And match $._returned == 1

Scenario: $where in the filter is refused
  Given path db, 'coll'
  And param filter = '{"$where": "this.a == 1"}'
  And header Authorization = admin
  When method GET
  Then status 403

Scenario: $function nested in the filter is refused
  Given path db, 'coll'
  And param filter = '{"$expr": {"$eq": [1, ' + fn + ']}}'
  And header Authorization = admin
  When method GET
  Then status 403

Scenario: a plain projection is served
  Given path db, 'coll'
  And param keys = '{"a": 1}'
  And header Authorization = admin
  When method GET
  Then status 200
  And match $._embedded['rh:doc'][0].a == 1

Scenario: $function in the keys of a collection request is refused
  Given path db, 'coll'
  And param keys = '{"x": ' + fn + '}'
  And header Authorization = admin
  When method GET
  Then status 403

Scenario: $function in the keys of a document request is refused
  Given path db, 'coll', 'doc'
  And param keys = '{"x": ' + fn + '}'
  And header Authorization = admin
  When method GET
  Then status 403

Scenario: $accumulator in the keys is refused
  Given path db, 'coll'
  And param keys = '{"x": {"$accumulator": {"init": "function() {}", "accumulate": "function() {}", "accumulateArgs": [], "merge": "function() {}", "lang": "js"}}}'
  And header Authorization = admin
  When method GET
  Then status 403

Scenario: a classic PATCH is served
  Given path db, 'coll', 'doc'
  And header Authorization = admin
  And request { "c": 3 }
  When method PATCH
  Then status 200

Scenario: a PATCH with a safe aggregation pipeline is served
  Given path db, 'coll', 'doc'
  And header Authorization = admin
  And request [ { "$set": { "b": 2 } } ]
  When method PATCH
  Then status 200

  Given path db, 'coll', 'doc'
  And header Authorization = admin
  When method GET
  Then status 200
  And match $.b == 2

Scenario: a PATCH with $function in its aggregation pipeline is refused
  Given path db, 'coll', 'doc'
  And header Authorization = admin
  And request [ { "$set": { "x": { "$function": { "body": "function() { return 1; }", "args": [], "lang": "js" } } } } ]
  When method PATCH
  Then status 403
  And match $.message contains "update pipeline security violation"
  And match $.message contains "$function"

  Given path db, 'coll', 'doc'
  And header Authorization = admin
  When method GET
  Then status 200
  And match $.x == '#notpresent'

Scenario: a PATCH with a blacklisted stage in its aggregation pipeline is refused
  Given path db, 'coll', 'doc'
  And header Authorization = admin
  And request [ { "$lookup": { "from": "other", "localField": "a", "foreignField": "a", "as": "joined" } } ]
  When method PATCH
  Then status 403
  And match $.message contains "update pipeline security violation"
  And match $.message contains "$lookup"
