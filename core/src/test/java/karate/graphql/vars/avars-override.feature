Feature: A caller cannot supply a value for a server-resolved @ variable (restheart#727)

  An aggregation binds its $vars from ?avars, which the caller writes. If a supplied value could
  stand in for @user, then a pipeline written as {"$var": "@user.userid"} — the way an operator
  scopes an aggregation to whoever is asking — could be pointed at somebody else by asking.
  A @ name is resolved by the server or not at all.

  Its own feature file because the GraphQL scenarios set a `path` in their Background, and karate
  appends to it.

Background:
    * url restheartBaseURL

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

    * def setupData = callonce read('setup.feature')

  Scenario: the aggregation is scoped to the caller, not to the supplied @user

    Given path '/test-graphql/test-vars/_aggrs/mine'
    And header Authorization = owner1
    And param rep = 's'
    And param avars = '{"@user": {"userid": "aclowner2"}}'
    When method GET
    Then status 200
    # aclowner1's two documents. "charlie" belongs to aclowner2 and was asked for by name.
    And match $[*].name == ['alpha', 'bravo']

  Scenario: without the supplied avar the answer is the same

    # the control: the supplied value changed nothing, rather than the pipeline being broken
    Given path '/test-graphql/test-vars/_aggrs/mine'
    And header Authorization = owner1
    And param rep = 's'
    When method GET
    Then status 200
    And match $[*].name == ['alpha', 'bravo']

  Scenario: nor through the flat query-parameter shorthand

    # Since 9.9 any non-reserved query param is merged into avars, so ?@user=... reaches exactly
    # the same map as ?avars={"@user":...}. It is a second spelling of the same attempt, not a
    # second door, and it must be refused by the same rule.
    Given path '/test-graphql/test-vars/_aggrs/mine'
    And header Authorization = owner1
    And param rep = 's'
    And param @user = '{"userid": "aclowner2"}'
    When method GET
    Then status 200
    And match $[*].name == ['alpha', 'bravo']

  Scenario: the rule covers every registered variable, not a list of known names

    # The protection is keyed on "does a resolver claim this name", so a plugin registering its
    # own — @subscription, say — is covered the day it registers, with nobody remembering to add
    # it anywhere. Here the pipeline does not even use @now; what matters is that supplying it
    # cannot bind anything, so it cannot displace a resolver's answer.
    Given path '/test-graphql/test-vars/_aggrs/mine'
    And header Authorization = owner1
    And param rep = 's'
    And param @now = '"not a date"'
    And param @subscription = '{"plan": "enterprise"}'
    And param @roles = '["admin"]'
    When method GET
    Then status 200
    And match $[*].name == ['alpha', 'bravo']
