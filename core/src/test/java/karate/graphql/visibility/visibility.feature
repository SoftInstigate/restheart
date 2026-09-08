Feature: @visible hides a field from the roles it does not name (restheart#478)

  A hidden field is removed from the schema for that caller, not nulled at execution: asking for
  it is a validation error saying the field does not exist, and introspection never mentions it.
  A caller who may not see a field does not learn it is there.

  Three callers, one app: aclowner1 has no role the directive names, hruser has "hr", admin has
  "admin". Nothing else about them differs.

Background:
    * url graphQLBaseURL
    * path 'test-visible'

    * def basic =
    """
    function(creds) {
    var temp = creds.username + ':' + creds.password;
    var Base64 = Java.type('java.util.Base64');
    var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
    return 'Basic ' + encoded;
    }
    """

    * def plain = basic({username: 'aclowner1', password: 'secret'})
    * def hr = basic({username: 'hruser', password: 'secret'})
    * def admin = basic({username: 'admin', password: 'secret'})

    * def setupData = callonce read('setup.feature')

  Scenario: an undecorated field is readable by everyone

    * text query =
    """
    { people { name } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = plain
    And request query
    When method POST
    Then status 200
    And match $.data.people == [ { name: 'ada' }, { name: 'borg' } ]

  Scenario: a role the directive does not name cannot ask for the field at all

    # not "returns null": the field does not exist in that caller's schema
    * text query =
    """
    { people { name salary } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = plain
    And request query
    When method POST
    Then assert responseStatus == 400 || responseStatus == 200
    And match $.errors == '#present'
    And match $.errors[0].message contains 'salary'

  Scenario: a role the directive names reads the field

    * text query =
    """
    { people { name salary } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = hr
    And request query
    When method POST
    Then status 200
    And match $.data.people == [ { name: 'ada', salary: 100 }, { name: 'borg', salary: 200 } ]

  Scenario: naming one role does not grant the others

    # hr is in salary's list, not in ssn's
    * text query =
    """
    { people { ssn } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = hr
    And request query
    When method POST
    Then assert responseStatus == 400 || responseStatus == 200
    And match $.errors == '#present'

  Scenario: a role named by every directive reads everything

    * text query =
    """
    { people { name salary ssn } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = admin
    And request query
    When method POST
    Then status 200
    And match $.data.people[0] == { name: 'ada', salary: 100, ssn: 'AAA-1' }

  Scenario: a hidden field is absent from introspection

    # the property a visibility directive exists for: the caller does not learn the field is there
    * text query =
    """
    { __type(name: "Person") { fields { name } } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = plain
    And request query
    When method POST
    Then status 200
    And match $.data.__type.fields[*].name == ['name']

  Scenario: introspection shows each caller their own schema

    * text query =
    """
    { __type(name: "Person") { fields { name } } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = hr
    And request query
    When method POST
    Then status 200
    And match $.data.__type.fields[*].name contains 'salary'
    And match $.data.__type.fields[*].name !contains 'ssn'

  Scenario: a Query field can be hidden too

    * text query =
    """
    { audit { name } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = hr
    And request query
    When method POST
    Then assert responseStatus == 400 || responseStatus == 200
    And match $.errors == '#present'

  Scenario: and is readable by the role that names it

    * text query =
    """
    { audit { name } }
    """

    Given header Content-Type = contTypeGraphQL
    And header Authorization = admin
    And request query
    When method POST
    Then status 200
    And match $.data.audit == [ { name: 'ada' }, { name: 'borg' } ]
