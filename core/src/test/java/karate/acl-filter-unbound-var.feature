Feature: an unbound ACL variable never widens access nor stamps a null owner

  Until 9.x an ACL variable that could not be resolved became null in a readFilter, so
  {"owner": "@user._id"} turned into {"owner": null}, which in MongoDB matches every document
  without an owner. A JWT account only has the token claims (sub, roles, ...), never _id, so
  the documented per-user ownership pattern let every JWT user read all the unowned documents.

  The unbound variable is now replaced with a random value that matches nothing, while the
  rest of the filter keeps working: here the {"public": true} branch still applies.

Background:
    * url 'http://localhost:8080'
    * callonce read('acl-filter-unbound-var-setup.feature')

    * def basic =
    """
    function(creds) {
    var temp = creds.username + ':' + creds.password;
    var Base64 = Java.type('java.util.Base64');
    var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
    return 'Basic ' + encoded;
    }
    """

    * def coll = '/test-acl-unbound/docs'


Scenario: with Basic auth the unowned documents are not readable

    * header Authorization = basic({username: 'unboundreader', password: 'secret'})
    Given path coll
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'
    And match response[0].name == 'public'


Scenario: with a JWT the unowned documents are not readable

    Given path '/token'
    And header Authorization = basic({username: 'unboundreader', password: 'secret'})
    When method POST
    Then status 200
    * def token = response.access_token

    * header Authorization = 'Bearer ' + token
    Given path coll
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'
    And match response[0].name == 'public'


Scenario: a mergeRequest with an unbound @user variable refuses the write

    # {"owner": "@user._id"} would otherwise store owner: null, a document nobody owns
    * header Authorization = basic({username: 'unboundreader', password: 'secret'})
    Given path coll
    And request { "name": "mine" }
    When method POST
    Then status 403
    And match response.message contains '@user._id'


Scenario: a mergeRequest with a bound @user variable stamps it

    * header Authorization = basic({username: 'unboundreader', password: 'secret'})
    Given path '/test-acl-unbound/stamped'
    And request { "name": "mine", "owner": "someone-else" }
    When method POST
    Then status 201

    * def id = responseHeaders['Location'][0].substring(responseHeaders['Location'][0].lastIndexOf('/') + 1)
    * header Authorization = 'Basic YWRtaW46c2VjcmV0'
    Given path '/test-acl-unbound/stamped/' + id
    When method GET
    Then status 200
    And match response.owner == 'unboundreader'
