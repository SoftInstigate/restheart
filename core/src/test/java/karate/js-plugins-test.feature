@requires-graalvm
Feature: Test javascript plugins written before the SDK (export const options, export function handle)

Background:
    * url 'http://localhost:8080'

    * def ensureJsPlugins =
    """
    function() {
        var CopyFolderRecursively = Java.type('karate.CopyFolderRecursively');
        new CopyFolderRecursively().ensure('test-js-plugins', ['helloWorldService', 'helloWorldInterceptor']);
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

Scenario: a legacy service and a legacy interceptor keep working unchanged
    * call ensureJsPlugins
    * header Authorization = admin
    Given path '/hello'
    When method GET
    Then status 200
    And match response.msg == 'Hello World!'
    And match response.note == 'modified by helloWorldInterceptor'
