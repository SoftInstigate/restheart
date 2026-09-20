Feature: a caller cannot supply a value for a server-resolved @ variable

  An aggregation binds its $vars from ?avars, which the caller writes. A pipeline scoped as
  {"$var": "@user.userid"} — the way an operator scopes an aggregation to whoever is asking —
  could then be pointed at somebody else by asking. A @ name is bound by the server or not at
  all, and a supplied one is dropped rather than refused, so the request still runs.

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
    * callonce read('acl-server-resolved-vars-setup.feature')


Scenario: the aggregation is scoped to the caller, not to the supplied @user

    * header Authorization = admin
    Given path '/test-server-vars/docs/_aggrs/mine'
    And param avars = '{"@user.userid": "someone-else"}'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'
    And match response[0].name == 'admins-own'


Scenario: nor through the flat query-parameter shorthand

    * header Authorization = admin
    Given path '/test-server-vars/docs/_aggrs/mine'
    And params { '@user.userid': 'someone-else', rep: 's' }
    When method GET
    Then status 200
    And match response == '#[1]'
    And match response[0].name == 'admins-own'


Scenario: without any supplied value the answer is the same

    # the control: the supplied value changed nothing, rather than the pipeline being broken
    * header Authorization = admin
    Given path '/test-server-vars/docs/_aggrs/mine'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'
    And match response[0].name == 'admins-own'
