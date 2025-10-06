Feature: Setup for Aggregation Security Tests

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

@ignore
Scenario: Setup test database and collection for aggregation security tests

  # Create test database
  Given path '/test-aggr-security'
  And header Authorization = admin
  When method PUT
  Then status 201

  # Create test collection
  Given path '/test-aggr-security/coll'
  And header Authorization = admin
  When method PUT
  Then status 201

  # Insert test documents
  Given path '/test-aggr-security/coll'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  [
    {"name": "doc1", "category": "A", "value": 100},
    {"name": "doc2", "category": "B", "value": 200},
    {"name": "doc3", "category": "A", "value": 150}
  ]
  """
  When method POST
  Then status 200

  # Create aggregation with safe operations
  Given path '/test-aggr-security/coll/_aggrs/safe-aggr'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "type": "pipeline",
    "stages": [
      {"$match": {"category": "A"}},
      {"$group": {"_id": "$category", "total": {"$sum": "$value"}}},
      {"$sort": {"total": -1}}
    ]
  }
  """
  When method PUT
  Then status 201

  # Create aggregation with $out stage (should be blocked)
  Given path '/test-aggr-security/coll/_aggrs/unsafe-out'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "type": "pipeline",
    "stages": [
      {"$match": {"category": "A"}},
      {"$out": "malicious_output"}
    ]
  }
  """
  When method PUT
  Then status 201

  # Create aggregation with $lookup cross-database (should be blocked)
  Given path '/test-aggr-security/coll/_aggrs/unsafe-lookup'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "type": "pipeline", 
    "stages": [
      {
        "$lookup": {
          "from": "other-db.users",
          "localField": "user_id",
          "foreignField": "_id",
          "as": "user_info"
        }
      }
    ]
  }
  """
  When method PUT
  Then status 201

  # Create aggregation with $where operator (should be blocked)
  Given path '/test-aggr-security/coll/_aggrs/unsafe-where'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "type": "pipeline",
    "stages": [
      {"$match": {"$where": "function() { return this.value > 100; }"}}
    ]
  }
  """
  When method PUT
  Then status 201

  # Create aggregation with $function operator (should be blocked)
  Given path '/test-aggr-security/coll/_aggrs/unsafe-function'
  And header Authorization = admin  
  And header Content-Type = 'application/json'
  And request
  """
  {
    "type": "pipeline",
    "stages": [
      {
        "$addFields": {
          "doubled": {
            "$function": {
              "body": "function(value) { return value * 2; }",
              "args": ["$value"],
              "lang": "js"
            }
          }
        }
      }
    ]
  }
  """
  When method PUT
  Then status 201