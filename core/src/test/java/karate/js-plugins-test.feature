@requires-graalvm
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

    * def copyJsPluginDir =
    """
    function() {
        var CopyFolderRecursively = Java.type('karate.CopyFolderRecursively');
        var cp = new CopyFolderRecursively();
        cp.copyFolder();
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

    * def admin = basic({username: 'admin', password: 'secret'})

Scenario: Load JS plugins and test hello service
    * call copyJsPluginDir
    * call sleep 10
    * header Authorization = admin
    Given path '/hello'
    When method GET
    Then status 200
    And match response.msg == 'Hello World!'
    And match response.note == 'modified by helloWorldInterceptor'


