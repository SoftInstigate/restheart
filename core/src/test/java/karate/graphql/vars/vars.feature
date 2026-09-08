Feature: The @ variables a GraphQL mapping can resolve (restheart#727)

  A mapping used to see one variable, @user, and only in an aggregation mapping: a query
  mapping's find could not use it, and nothing else — @now, @roles, @qparams — worked in
  either. These scenarios pin down what a mapping can rely on, and, just as importantly,
  that a name nobody registered still fails instead of quietly becoming a literal string.

Background:
    * url graphQLBaseURL
    * path 'test-vars'

    * def basic =
    """
    function(creds) {
    var temp = creds.username + ':' + creds.password;
    var Base64 = Java.type('java.util.Base64');
    var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
    return 'Basic ' + encoded;
    }
    """

    * def owner1 = basic({username: 'aclowner1', password: 'secret'})
    * def owner2 = basic({username: 'aclowner2', password: 'secret'})

    * def setupData = callonce read('setup.feature')

  Scenario: @user resolves in a query mapping's find

    # this is the one that did not work: @user reached aggregation mappings only
    * text query =
    """
    { myThings { name owner } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = owner1
    And request query
    When method POST
    Then status 200
    And match $.data.myThings == [ { name: 'alpha', owner: 'aclowner1' }, { name: 'bravo', owner: 'aclowner1' } ]

  Scenario: the same query answers differently for a different caller

    # the whole point of the variable: one mapping, one query, scoped to whoever is asking
    * text query =
    """
    { myThings { name owner } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = owner2
    And request query
    When method POST
    Then status 200
    And match $.data.myThings == [ { name: 'charlie', owner: 'aclowner2' }, { name: 'future', owner: 'aclowner2' } ]

  Scenario: @user still resolves in an aggregation mapping

    # it worked before; it must keep working, now through the shared path
    * text query =
    """
    { myThingsAggr { name owner } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = owner1
    And request query
    When method POST
    Then status 200
    And match $.data.myThingsAggr == [ { name: 'alpha', owner: 'aclowner1' }, { name: 'bravo', owner: 'aclowner1' } ]

  Scenario: @now resolves, and is a real instant rather than a literal

    # every document created in the past matches, the one dated 2999 does not
    * text query =
    """
    { pastThings { name } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = owner1
    And request query
    When method POST
    Then status 200
    And match $.data.pastThings == [ { name: 'alpha' }, { name: 'bravo' }, { name: 'charlie' } ]

  Scenario: @roles resolves to the caller's roles, as an array

    * text query =
    """
    { roleThings { name role } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = owner1
    And request query
    When method POST
    Then status 200
    And match $.data.roleThings == [ { name: 'alpha', role: 'aclreader' } ]

  Scenario: a name no resolver claims is unbound, not a literal string

    # the failure this must never have: @usr.userid becoming the string "@usr.userid",
    # matching nothing silently — or worse, matching a document that happens to contain it
    * text query =
    """
    { mistypedVar { name } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = owner1
    And request query
    When method POST
    Then assert responseStatus == 200 || responseStatus == 400
    And match $.errors == '#present'
    And match $ !contains { data: { mistypedVar: '#[_ > 0]' } }
