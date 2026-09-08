@ignore
Feature: Seed the fixture for avars-flat-qparams

  Called with callonce so the data is created once per feature: the Background of
  the calling feature runs per scenario, and re-POSTing the documents each time
  would make every count and sum depend on how many scenarios ran before.

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


Scenario: create the database, the collection and its documents

    * header Authorization = admin
    Given path '/test-avars-flat'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # byStatus  — a bare {"$var": "status"}: no default, so it must be bound
    # limited   — {"$var": "max"} used where MongoDB demands a number, so a 200 proves
    #             the flat value was parsed as one rather than passed through as a string
    # byPage    — {"$var": "page"} on a RESERVED name: must never be bound from ?page=
    * header Authorization = admin
    Given path '/test-avars-flat/purchases'
    And request
    """
    {
      "aggrs": [
        {
          "uri": "byStatus",
          "stages": [
            { "$match": { "status": { "$var": "status" } } },
            { "$group": { "_id": "$item", "total": { "$sum": "$qty" } } }
          ]
        },
        {
          "uri": "limited",
          "stages": [
            { "$sort": { "qty": 1 } },
            { "$limit": { "$var": "max" } }
          ]
        },
        {
          "uri": "byPage",
          "stages": [
            { "$match": { "item": { "$var": "page" } } }
          ]
        }
      ]
    }
    """
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = admin
    Given path '/test-avars-flat/purchases'
    And request { "item": "widget", "qty": 7, "status": "A" }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = admin
    Given path '/test-avars-flat/purchases'
    And request { "item": "gadget", "qty": 3, "status": "D" }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = admin
    Given path '/test-avars-flat/purchases'
    And request { "item": "sprocket", "qty": 5, "status": "A" }
    When method POST
    Then assert responseStatus == 201
