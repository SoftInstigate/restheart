Feature: Bind aggregation $var from flat query parameters

  Since 9.9.0 a $var can be bound by a plain query parameter named after it
  (?status=A), not only by the avars JSON object (?avars={"status":"A"}).
  Both forms coexist; on a name clash the explicit avars value wins.

  This is a REST-level feature, exercised against the real /_aggrs/ endpoint —
  the same binding also reaches change streams, since both go through
  MongoRequestPropsInjector.

Background:
    * url 'http://localhost:8080'
    * callonce read('avars-flat-setup.feature')

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
    * def coll = '/test-avars-flat/purchases'
    * def aggr = coll + '/_aggrs/byStatus'


Scenario: a flat query parameter binds the $var of the same name

    * header Authorization = admin
    Given path aggr
    And param status = 'A'
    And param rep = 's'
    When method GET
    Then status 200
    # widget (7) and sprocket (5) are status A; gadget (status D) is excluded
    And match response == '#[2]'
    And match response contains { _id: 'widget', total: 7 }
    And match response contains { _id: 'sprocket', total: 5 }


Scenario: the legacy avars object still binds it

    * header Authorization = admin
    Given path aggr
    And param avars = '{"status": "D"}'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == [{ _id: 'gadget', total: 3 }]


Scenario: on a name clash the explicit avars value wins

    * header Authorization = admin
    Given path aggr
    And param avars = '{"status": "D"}'
    And param status = 'A'
    And param rep = 's'
    When method GET
    Then status 200
    # D, from avars — not A from the flat param
    And match response == [{ _id: 'gadget', total: 3 }]


Scenario: a JSON-shaped flat value is bound as a real number, not a string

    # $limit rejects a string, so a 200 here is itself the proof that the value
    # was parsed rather than passed through verbatim
    * header Authorization = admin
    Given path coll + '/_aggrs/limited'
    And param max = 2
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[2]'


Scenario: an unbound required $var is still an error

    # the shorthand adds a way to bind variables; it must not silently invent one
    * header Authorization = admin
    Given path aggr
    And param rep = 's'
    When method GET
    Then status 400
    And match response.message contains 'aggregation'


Scenario: a reserved query parameter is not bound as a variable

    # 'page' has framework meaning, so it must never reach the pipeline as a $var —
    # otherwise a paginated request would silently change what the aggregation matches.
    # byPage references {"$var": "page"}, so if ?page= were bound this would return 200;
    # it must instead fail as unbound, exactly as if nothing had been passed.
    * header Authorization = admin
    Given path coll + '/_aggrs/byPage'
    And param page = 1
    And param rep = 's'
    When method GET
    Then status 400
    And match response.message contains 'aggregation'
