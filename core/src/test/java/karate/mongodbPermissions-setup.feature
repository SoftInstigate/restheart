@ignore
Feature: Setup for mongodb permissions test

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
    * def dbName = '/test-all-permissions/'

Scenario: Prepare data

    # create db test-db
    * header Authorization = admin
    Given path db
    When method PUT
    * def ETAG = responseHeaders['ETag'][0]


    # create allowAll collection
    * header Authorization = admin
    Given path db + coll
    When method PUT
