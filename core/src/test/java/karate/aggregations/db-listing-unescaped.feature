Feature: Aggregation stages read back unescaped from the db listing

  A pipeline is stored with its dollar-prefixed keys escaped, `_$skip` for `$skip`, and
  AggregationTransformer restores them on read. The db listing carries every collection's
  properties, pipelines included, and has to be restored the same way as the collection's own
  metadata: a client reading the pipelines from GET /db used to get the stored form. Only the HAL
  representation of a db carries the properties; the standard one lists the names alone.

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

    * def setupData = callonce read('setup.feature')

    * def firstStageKeys =
    """
    function(coll) { return karate.keysOf(coll.aggrs[0].stages[0]); }
    """

Scenario: The collection's own metadata restores the keys

    * header Authorization = admin
    Given path '/test-aggregations/test/_meta'
    And param rep = 's'
    When method GET
    Then status 200
    * def keys = firstStageKeys(response)
    And match keys contains '$skip'
    And match keys !contains '_$skip'

Scenario: The db listing restores them too

    * header Authorization = admin
    Given path '/test-aggregations'
    And param rep = 'hal'
    When method GET
    Then status 200
    * def coll = karate.filter(response._embedded['rh:coll'], function(c) { return c._id == 'test' })[0]
    * match coll == '#object'
    * def keys = firstStageKeys(coll)
    And match keys contains '$skip'
    And match keys !contains '_$skip'
