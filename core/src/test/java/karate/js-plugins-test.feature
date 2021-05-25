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

    * def copyJsPluginDir = 
    """
    function() {
        var CopyFolderRecursively = Java.type('karate.CopyFolderRecursively');
        var cp = new CopyFolderRecursively();
        cp.copyFolder();
    }
    """


Scenario:
    * call copyJsPluginDir
    * call sleep 2
    Given path '/hello'
    When method GET
    Then status 200
    And match response == {"msg":"Hello World from Italy with Love","note":"'from Italy with Love' was added by 'helloWorldInterceptor' that modifies the response of 'helloWorldService'"}