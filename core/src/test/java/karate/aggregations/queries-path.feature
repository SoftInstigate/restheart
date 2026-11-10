Feature: /coll/_queries/<name>, the suggested path of an aggregation since 9.16

  _queries runs the same aggregations as _aggrs, which stays for backward compatibility.
  The links of a collection point to _queries.

Background:
    * url 'http://localhost:8080'
    * callonce read('queries-path-setup.feature')

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
    * def coll = '/test-queries-path/purchases'


Scenario: _queries and _aggrs run the same aggregation

    * header Authorization = admin
    Given path coll + '/_queries/byStatus'
    And param status = 'A'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[2]'
    * def viaQueries = response

    * header Authorization = admin
    Given path coll + '/_aggrs/byStatus'
    And param status = 'A'
    And param rep = 's'
    When method GET
    Then status 200
    # $group gives no order: the same results, in any order
    And match response contains only viaQueries


Scenario: _queries alone answers as _aggrs alone

    * header Authorization = admin
    Given path coll + '/_aggrs'
    When method GET
    * def aggrsStatus = responseStatus

    * header Authorization = admin
    Given path coll + '/_queries'
    When method GET
    Then match responseStatus == aggrsStatus


Scenario: the links of a collection point to _queries

    * header Authorization = admin
    Given path coll
    And param rep = 'hal'
    When method GET
    Then status 200
    And match response._links.byStatus.href contains '/_queries/byStatus'
