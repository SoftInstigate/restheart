@only
Feature: Test javascript plugins

Background:
    * url 'http://localhost:8080'

    * def sleep =
    """
    function(seconds) {
        for(i = 0; i <= seconds; i++) {
            java.lang.Thread.sleep(1*1000);
            karate.log(i);
        }
    }
    """


    * def basic =
    """
    function(creds) {
    var temp = creds.username + ':' + creds.password;
    var Base64 = Java.type('java.util.Base64');
    var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
    return 'Basic ' + encoded;
    }
    """

    * def copyJsPluginDir = 
    """
    function() {
        var CopyFolderRecursively = Java.type('karate.CopyFolderRecursively');
        var cp = new CopyFolderRecursively();
        cp.copyFolder();
    }
    """

    * def admin = basic({username: 'admin', password: 'secret'})


Scenario: Test hello
    * call copyJsPluginDir
    * call sleep 2
    Given path '/hello'
    When method GET
    Then status 200
    And match response == {"msg":"Hello World from Italy with Love","note":"'from Italy with Love' was added by 'helloWorldInterceptor' that modifies the response of 'helloWorldService'"}


Scenario: Test sub hello

    Given path '/sub/hello'
    When method GET
    Then status 200


Scenario: Test http client

    Given path '/httpClient'
    When method GET
    Then status 200


Scenario: Test mclient-service

    # create test-db
    * header Authorization = admin
    Given path '/test-db'
    When method PUT
    * def ETAG = responseHeaders['ETag'][0]
    

    # create collection coll
    * header Authorization = admin
    Given path '/test-db/coll'
    When method PUT
    

    # send request
    * header Authorization = admin
    Given path '/mclientService'
    When method GET
    Then status 200


    # delete test-db
    * headers { "Authorization" : '#(admin)', "If-Match" : "#(ETAG)" }
    Given path '/test-db'
    When method DELETE
    And status 204


