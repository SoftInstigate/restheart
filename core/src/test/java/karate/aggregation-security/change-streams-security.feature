Feature: Test Change Streams Aggregation Pipeline Security

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

Scenario: Setup change streams with safe and unsafe pipelines in collection metadata
  # Create test database for change streams
  Given path '/test-stream-security'
  And header Authorization = admin
  When method PUT
  Then assert responseStatus == 201 || responseStatus == 200

  # Create test collection for change streams
  Given path '/test-stream-security/coll'
  And header Authorization = admin
  When method PUT
  Then assert responseStatus == 201 || responseStatus == 200

  # Insert test data
  Given path '/test-stream-security/coll'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  [
    {"name": "doc1", "value": 100},
    {"name": "doc2", "value": 200}
  ]
  """
  When method POST
  Then assert responseStatus == 201 || responseStatus == 200

  # Define change streams in collection metadata (both safe and unsafe)
  Given path '/test-stream-security/coll'
  And param wm = 'upsert'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "streams": [
      {
        "uri": "safe-stream",
        "stages": [
          {"$match": {"operationType": "insert"}},
          {"$project": {"fullDocument": 1, "operationType": 1}}
        ]
      },
      {
        "uri": "unsafe-stream",
        "stages": [
          {"$match": {"operationType": "insert"}},
          {"$out": "malicious_stream_output"}
        ]
      }
    ]
  }
  """
  When method PATCH
  Then assert responseStatus == 201 || responseStatus == 200

Scenario: Safe change stream should work normally
  Given path '/test-stream-security/coll/_streams/safe-stream'
  And header Authorization = admin
  And header Connection = 'Upgrade'
  And header Upgrade = 'websocket'
  When method GET
  # Note: WebSocket upgrade is not fully testable in Karate, but should not fail due to security
  Then assert responseStatus != 403

Scenario: Unsafe change stream should be blocked when processing events
  Given path '/test-stream-security/coll/_streams/unsafe-stream'  
  And header Authorization = admin
  And header Connection = 'Upgrade'
  And header Upgrade = 'websocket'
  When method GET
  # Should be blocked due to $out stage in the pipeline
  # The security check should happen during stream processing
  Then assert responseStatus == 403 || responseStatus == 400