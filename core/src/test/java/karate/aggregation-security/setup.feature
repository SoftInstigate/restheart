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

  # Define aggregations in collection metadata
  Given path '/test-aggr-security/coll'
  And param wm = 'upsert'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "aggrs": [
      {
        "uri": "safe-aggr",
        "stages": [
          {"$match": {"category": "A"}},
          {"$group": {"_id": "$category", "total": {"$sum": "$value"}}},
          {"$sort": {"total": -1}}
        ]
      },
      {
        "uri": "unsafe-out",
        "stages": [
          {"$match": {"category": "A"}},
          {"$out": "malicious_output"}
        ]
      },
      {
        "uri": "unsafe-lookup",
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
      },
      {
        "uri": "unsafe-where",
        "stages": [
          {"$match": {"$where": "function() { return this.value > 100; }"}}
        ]
      },
      {
        "uri": "unsafe-function",
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
    ]
  }
  """
  When method PATCH
  Then status 200