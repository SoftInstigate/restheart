@requires-graalvm
Feature: JavaScript plugins whose files are copied before package.json are deployed (#804)

    The deployer deploys a plugin file when it appears if package.json declares it. A plugin
    directory copied with its files before package.json was partly deployed: the files that
    appeared first were never deployed. They are now deployed when package.json appears.

Background:
    * url 'http://localhost:8080'

    * def copyPackageJsonLast =
    """
    function() {
        var CopyFolderRecursively = Java.type('karate.CopyFolderRecursively');
        new CopyFolderRecursively().copyPackageJsonLast('test-js-plugins-late', ['lateDeclaredService', 'lateDeclaredInterceptor']);
    }
    """

Scenario: a service and an interceptor copied before package.json are deployed
    * call copyPackageJsonLast
    Given path '/late-declared'
    When method GET
    Then status 200
    And match response == 'late!'
