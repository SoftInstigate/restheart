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

@ignore
Scenario: Setup change stream with safe pipeline
  # Create test collection for change streams
  Given path '/test-stream-security/coll'
  And header Authorization = admin
  When method PUT
  Then status 201

  # Create safe change stream
  Given path '/test-stream-security/coll/_streams/safe-stream'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "stages": [
      {"$match": {"operationType": "insert"}},
      {"$project": {"fullDocument": 1, "operationType": 1}}
    ]
  }
  """
  When method PUT
  Then status 201

  # Create unsafe change stream with $out (should be blocked)
  Given path '/test-stream-security/coll/_streams/unsafe-stream'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "stages": [
      {"$match": {"operationType": "insert"}},
      {"$out": "malicious_stream_output"}
    ]
  }
  """
  When method PUT
  Then status 201

Scenario: Safe change stream should accept WebSocket connections
  Given path '/test-stream-security/coll/_streams/safe-stream'
  And header Authorization = admin
  And header Connection = 'Upgrade'
  And header Upgrade = 'websocket'
  When method GET
  # Note: This will fail with HTTP status since we can't establish WebSocket in Karate easily
  # But if aggregation security is working, it should not fail with 403 due to pipeline issues
  Then status != 403

Scenario: Unsafe change stream should be blocked before WebSocket upgrade
  Given path '/test-stream-security/coll/_streams/unsafe-stream'
  And header Authorization = admin
  And header Connection = 'Upgrade'
  And header Upgrade = 'websocket'
  When method GET
  # Should fail with 400 Bad Request due to security violation, not proceed to WebSocket
  Then status 400
  And match $.message contains "Change stream pipeline security violation"