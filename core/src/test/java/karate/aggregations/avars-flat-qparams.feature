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


Scenario: an avars value with an operator is refused

    # aggregation-check-operators is true by default: a variable value is data, never an operator
    * header Authorization = admin
    Given path aggr
    And param avars = '{"status": {"$gt": ""}}'
    And param rep = 's'
    When method GET
    Then status 400


Scenario: a flat query parameter with an operator is refused, like avars

    # before 9.15.3 the flat form skipped the check that avars has: now a parameter the
    # pipeline reads with $var or $ifvar is checked
    * header Authorization = admin
    Given path aggr
    And param status = '{"$gt": ""}'
    And param rep = 's'
    When method GET
    Then status 400
    And match response.message contains 'illegal query parameter status'


Scenario: a query parameter with an operator that the pipeline does not read is not a variable

    # byStatus reads only status: extra never reaches the pipeline, so it is not checked
    * header Authorization = admin
    Given path aggr
    And param status = 'A'
    And param extra = '{"$match": {}}'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[2]'


Scenario: an avars value with an operator is refused even if the pipeline does not read it

    # avars declares variables: all of them are checked
    * header Authorization = admin
    Given path aggr
    And param status = 'A'
    And param avars = '{"x": {"$ne": 1}}'
    And param rep = 's'
    When method GET
    Then status 400


Scenario: a query parameter with an operator that an $ifvar reads is refused

    * header Authorization = admin
    Given path coll + '/_aggrs/optionalStatus'
    And param onlyA = '{"$ne": null}'
    And param rep = 's'
    When method GET
    Then status 400
    And match response.message contains 'illegal query parameter onlyA'

    * header Authorization = admin
    Given path coll + '/_aggrs/optionalStatus'
    And param onlyA = true
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[2]'


Scenario: in $match a string starting with "$" is a value

    # a $match outside $expr is a query: "$status" is compared as a string, and matches nothing
    * header Authorization = admin
    Given path aggr
    And param status = '$status'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == []


Scenario: in $expr a string starting with "$" is bound in $literal, not read as a field path

    # read as the status field, the comparison would be true for every document
    * header Authorization = admin
    Given path coll + '/_aggrs/byStatusExpr'
    And param status = '$status'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == []

    * header Authorization = admin
    Given path coll + '/_aggrs/byStatusExpr'
    And param status = 'D'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == [{ item: 'gadget' }]


Scenario: in a projection a string starting with "$" is returned as it is

    * header Authorization = admin
    Given path coll + '/_aggrs/labelled'
    And param label = '$5'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == [{ label: '$5' }]


Scenario: a string that starts with "$" and a space is a value

    # "$ 5" is allowed: it matches no document here, and the request is not refused
    * header Authorization = admin
    Given path aggr
    And param status = '$ 5'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == []


Scenario: a flat query parameter with an operator does not fail a request that reads no variables

    # only aggregations and change streams read the variables: elsewhere the value is not bound
    * header Authorization = admin
    Given path coll
    And param status = '{"$gt": ""}'
    And param rep = 's'
    When method GET
    Then status 200
