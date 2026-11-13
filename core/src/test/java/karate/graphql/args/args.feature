Feature: GraphQL arguments are values (check-operators, enabled by default)

  A string argument starting with "$" is bound in $literal where MongoDB would read it as a field
  path or a variable, and is left as it is in a query; an argument with an operator, which only a
  BsonDocument argument passed as a GraphQL variable can carry, is refused.

Background:
    * url graphQLBaseURL
    * path 'test-args'

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

  Scenario: in an aggregation $expr a string starting with "$" is a value, not the owner field

    # read as the owner field, the comparison would be true for all four documents
    Given header Content-Type = contTypeGraphQL
    And header Authorization = admin
    And request '{ byOwnerExpr(owner: "$owner") { name } }'
    When method POST
    Then status 200
    And match $.data.byOwnerExpr == []

  Scenario: the same aggregation still filters by a plain value

    Given header Content-Type = contTypeGraphQL
    And header Authorization = admin
    And request '{ byOwnerExpr(owner: "aclowner2") { name } }'
    When method POST
    Then status 200
    And match $.data.byOwnerExpr == [ { name: 'charlie' }, { name: 'future' } ]

  Scenario: in a query mapping's $expr a string starting with "$" is a value

    Given header Content-Type = contTypeGraphQL
    And header Authorization = admin
    And request '{ byOwnerFindExpr(owner: "$owner") { name } }'
    When method POST
    Then status 200
    And match $.data.byOwnerFindExpr == []

  Scenario: in a query mapping's find a string starting with "$" is compared as it is

    Given header Content-Type = contTypeGraphQL
    And header Authorization = admin
    And request '{ byOwnerFind(owner: "$owner") { name } }'
    When method POST
    Then status 200
    And match $.data.byOwnerFind == []

  Scenario: a BsonDocument argument with an operator is refused

    * text query =
    """
    query q($o: BsonDocument) { byOwnerDoc(owner: $o) { name } }
    """
    * def variables = { o: { "$ne": null } }

    Given header Content-Type = contTypeJson
    And header Authorization = admin
    And request { query: '#(query)', variables: '#(variables)' }
    When method POST
    Then match $.errors == '#[_ > 0]'
    And match $.data.byOwnerDoc == '#null'

  Scenario: a BsonDocument argument without operators is a value

    * text query =
    """
    query q($o: BsonDocument) { byOwnerDoc(owner: $o) { name } }
    """
    * def variables = { o: { first: 'aclowner1' } }

    Given header Content-Type = contTypeJson
    And header Authorization = admin
    And request { query: '#(query)', variables: '#(variables)' }
    When method POST
    Then status 200
    And match $.data.byOwnerDoc == []

  Scenario: an argument declared with "operators": true accepts operators in an aggregation

    * text query =
    """
    query q($f: BsonDocument) { byFilter(filter: $f) { name } }
    """
    * def variables = { f: { owner: { "$in": ['aclowner2'] } } }

    Given header Content-Type = contTypeJson
    And header Authorization = admin
    And request { query: '#(query)', variables: '#(variables)' }
    When method POST
    Then status 200
    And match $.errors == '#notpresent'
    And match $.data.byFilter == [ { name: 'charlie' }, { name: 'future' } ]

  Scenario: an argument declared with "operators": true accepts operators in a query mapping

    * text query =
    """
    query q($f: BsonDocument!) { byFilterFind(filter: $f) { name } }
    """
    * def variables = { f: { owner: { "$in": ['aclowner1'] } } }

    Given header Content-Type = contTypeJson
    And header Authorization = admin
    And request { query: '#(query)', variables: '#(variables)' }
    When method POST
    Then status 200
    And match $.errors == '#notpresent'
    And match $.data.byFilterFind == [ { name: 'alpha' }, { name: 'bravo' } ]

  Scenario: the other arguments of the same mapping are still checked

    * text query =
    """
    query q($f: BsonDocument, $o: BsonDocument) { byFilterAndOwner(filter: $f, owner: $o) { name } }
    """
    * def variables = { f: { name: { "$ne": 'alpha' } }, o: { "$ne": null } }

    Given header Content-Type = contTypeJson
    And header Authorization = admin
    And request { query: '#(query)', variables: '#(variables)' }
    When method POST
    Then match $.errors == '#[_ > 0]'
    And match $.data.byFilterAndOwner == '#null'

  Scenario: a JavaScript operator in an argument declared with "operators": true is still refused

    * text query =
    """
    query q($f: BsonDocument!) { byFilterFind(filter: $f) { name } }
    """
    * def variables = { f: { "$where": 'true' } }

    Given header Content-Type = contTypeJson
    And header Authorization = admin
    And request { query: '#(query)', variables: '#(variables)' }
    When method POST
    Then match $.errors == '#[_ > 0]'
    And match $.data.byFilterFind == '#null'
