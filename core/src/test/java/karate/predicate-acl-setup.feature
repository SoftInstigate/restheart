@ignore
Feature: Prepare test data for predicates-acl feature


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

Scenario: Create db and collection

  # create db test-predicates
  * header Authorization = admin
  Given path '/test-predicates'
  When method PUT
  Then status 201
  * def ETag = responseHeaders['ETag'][0]


  # create coll collection
  * header Authorization = admin
  Given path 'test-predicates/coll'
  When method PUT
  Then status 201


  # create projectResponse collection
  * header Authorization = admin
  Given path 'test-predicates/projectResponse'
  When method PUT
  Then status 201



