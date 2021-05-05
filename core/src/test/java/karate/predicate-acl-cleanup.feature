@ignore
Feature: Clean up data for predicat-acl


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

Scenario:

  # delete database test-predicates
  * headers { "Authorization" : '#(admin)', 'If-Match': '#(ETag)' }
  Given path '/test-predicates'
  And param filter = '{"_id":{"$exists":true}}'
  When method DELETE
  And status 204