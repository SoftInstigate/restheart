@ignore
Feature: Prepare API keys for the api-key-auth feature

# The hashes below are sha256 of the key strings, which is what
# mongoApiKeyAuthenticator stores — the key itself never is:
#
#   printf 'rhak_valid'     | shasum -a 256
#   printf 'rhak_noroles'   | shasum -a 256
#   printf 'rhak_expired'   | shasum -a 256
#   printf 'rhak_poweruser' | shasum -a 256
#
# Keys live in a database of their own, created here db -> collection ->
# documents. Not restheart-test: that one holds users and acl and is not a place
# to be creating arbitrary collections in.
#
# The documents are written with PUT and `wm=upsert`. Without the write mode a
# PUT on a document that does not exist yet answers 404, since the default mode
# updates rather than creates — and upsert is also what makes this setup
# re-runnable, where a POST would add four more keys on every run.

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

Scenario: Create the test-apikeys db and seed the keys

  * header Authorization = admin
  Given path 'test-apikeys'
  When method PUT
  Then assert responseStatus == 201 || responseStatus == 200

  * header Authorization = admin
  Given path 'test-apikeys/keys'
  When method PUT
  Then assert responseStatus == 201 || responseStatus == 200

  # A working key. `admin` is the role the test ACL grants path-prefix "/" to,
  # so this one can reach /secho.
  * header Authorization = admin
  Given path 'test-apikeys/keys/valid'
  And param wm = 'upsert'
  And request
  """
  {
    "hash": "cff1ff8ac100e099a885d9079ebe17919408763bca38289196d38490f335ff42",
    "user": "admin",
    "roles": ["admin"]
  }
  """
  When method PUT
  Then assert responseStatus == 201 || responseStatus == 200

  # Same principal, no roles at all. Deny-by-default made observable: the
  # request authenticates and then reaches nothing.
  * header Authorization = admin
  Given path 'test-apikeys/keys/noroles'
  And param wm = 'upsert'
  And request
  """
  {
    "hash": "a6c408d4deabb91ca3e358f28ba7e21526249bb352e82696958b0bc30924fd45",
    "user": "admin",
    "roles": []
  }
  """
  When method PUT
  Then assert responseStatus == 201 || responseStatus == 200

  # Expired an hour ago. Present in the collection on purpose: a TTL index
  # reclaims lazily, so the authenticator has to refuse it on its own.
  * header Authorization = admin
  Given path 'test-apikeys/keys/expired'
  And param wm = 'upsert'
  And request
  """
  {
    "hash": "c8f0f2bb100cea93d40356502f09746413f4ebcffc2b2c411cdd9657d0af23c8",
    "user": "admin",
    "roles": ["admin"],
    "expiresAt": { "$date": 1000000000000 }
  }
  """
  When method PUT
  Then assert responseStatus == 201 || responseStatus == 200

  # A narrower role. The test ACL grants poweruser only GET /testdb, so this key
  # is what shows that the *specific* roles on the key document drive
  # authorization — not merely that some role arrived.
  * header Authorization = admin
  Given path 'test-apikeys/keys/poweruser'
  And param wm = 'upsert'
  And request
  """
  {
    "hash": "ca8dcb2d445daf0ce1415b6e3a87c5da3bf5811281dfd87592d61569e8d9f994",
    "user": "admin",
    "roles": ["poweruser"]
  }
  """
  When method PUT
  Then assert responseStatus == 201 || responseStatus == 200
