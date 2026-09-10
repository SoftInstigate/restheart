@invariants
Feature: Integrity rules that span documents

# A collection declares rules as `invariants` metadata: an aggregation that must return nothing —
# or something, with holdsWhen: notEmpty. They are evaluated after the write and inside its
# transaction, so what they judge is the state the write would leave behind; a broken rule aborts
# it. They are a property of the data, so they apply to every writer, admin included.

Background:
* url 'http://localhost:8080'
* def db = '/test-invariants'
* def accounts = db + '/accounts'
* def users = db + '/users'
* def authHeader = 'Basic YWRtaW46c2VjcmV0'

Scenario: Set up two collections, one rule each

    * header Authorization = authHeader
    Given path db
    And request { }
    When method PUT
    Then assert responseStatus == 201

    # "no balance is negative": the pipeline finds the offenders, and must find none
    * header Authorization = authHeader
    Given path accounts
    And request { "invariants": [ { "name": "noNegativeBalance", "message": "an account balance cannot be negative", "stages": [ { "$match": { "balance": { "$lt": 0 } } } ] } ] }
    When method PUT
    Then assert responseStatus == 201

    # "at least one admin remains": the same shape, read the other way round
    * header Authorization = authHeader
    Given path users
    And request { "invariants": [ { "name": "atLeastOneAdmin", "message": "the last admin cannot be removed", "holdsWhen": "notEmpty", "stages": [ { "$match": { "role": "admin" } }, { "$limit": 1 } ] } ] }
    When method PUT
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path accounts
    And request { "_id": "alice", "balance": 70 }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path accounts
    And request { "_id": "bob", "balance": 20 }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path users
    And request { "_id": "root", "role": "admin" }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path users
    And request { "_id": "joe", "role": "user" }
    When method POST
    Then assert responseStatus == 201

Scenario: A write that would break the rule is refused, and leaves nothing behind

    * header Authorization = authHeader
    Given path accounts + '/alice'
    When method GET
    Then assert responseStatus == 200
    * def before = response

    * header Authorization = authHeader
    Given path accounts + '/alice'
    And request { "$inc": { "balance": -100 } }
    When method PATCH
    Then assert responseStatus == 409
    And match response.invariant == 'noNegativeBalance'
    And match response.message contains 'balance cannot be negative'
    # the rows are the useful half: which document breaks the rule
    And match response.violations[0]._id == 'alice'
    And match response.violations[0].balance == -30

    # etag included: nothing was written, so there is nothing to have changed
    * header Authorization = authHeader
    Given path accounts + '/alice'
    When method GET
    Then match response == before

Scenario: A write that keeps the rule is applied

    * header Authorization = authHeader
    Given path accounts + '/alice'
    And request { "$inc": { "balance": -50 } }
    When method PATCH
    Then assert responseStatus == 200

    * header Authorization = authHeader
    Given path accounts + '/alice'
    When method GET
    Then match response.balance == 20

Scenario: An insert is checked like any other write

    * header Authorization = authHeader
    Given path accounts
    And request { "_id": "carol", "balance": -1 }
    When method POST
    Then assert responseStatus == 409
    And match response.invariant == 'noNegativeBalance'

    * header Authorization = authHeader
    Given path accounts + '/carol'
    When method GET
    Then assert responseStatus == 404

Scenario: A delete is checked too, and notEmpty reads the other way round

    # joe is not an admin, so removing him breaks nothing
    * header Authorization = authHeader
    Given path users + '/joe'
    When method DELETE
    Then assert responseStatus == 204

    # root is the last admin
    * header Authorization = authHeader
    Given path users + '/root'
    When method DELETE
    Then assert responseStatus == 409
    And match response.invariant == 'atLeastOneAdmin'
    And match response.message contains 'last admin'
    # a notEmpty rule has no offending rows to show: the message is the whole diagnostic
    And match response.violations == '#notpresent'

    * header Authorization = authHeader
    Given path users + '/root'
    When method GET
    Then assert responseStatus == 200

Scenario: A bulk write is refused as a whole

    * header Authorization = authHeader
    Given path accounts + '/bob'
    When method GET
    * def bobBefore = response

    # alice is at 20 and bob at 20: -25 puts both under zero, and neither may be written
    * header Authorization = authHeader
    Given path accounts + '/*'
    And param filter = '{"_id":{"$exists":true}}'
    And request { "$inc": { "balance": -25 } }
    When method PATCH
    Then assert responseStatus == 409
    And match response.invariant == 'noNegativeBalance'

    * header Authorization = authHeader
    Given path accounts + '/bob'
    When method GET
    Then match response == bobBefore

Scenario: Malformed invariants are refused when declared, not at the first write

    * header Authorization = authHeader
    Given path db + '/broken'
    And request { "invariants": [ { "name": "noStages" } ] }
    When method PUT
    Then assert responseStatus == 400
    And match response.message contains 'stages'

    * header Authorization = authHeader
    Given path db + '/broken'
    And request { "invariants": [ { "name": "dup", "stages": [ { "$match": { } } ] }, { "name": "dup", "stages": [ { "$match": { } } ] } ] }
    When method PUT
    Then assert responseStatus == 400
    And match response.message contains 'duplicated'

    * header Authorization = authHeader
    Given path db + '/broken'
    And request { "invariants": [ { "name": "wrongDirection", "holdsWhen": "sometimes", "stages": [ { "$match": { } } ] } ] }
    When method PUT
    Then assert responseStatus == 400

    # $lookup is in aggregationSecurity's stageBlacklist, and invariants are held to it unchanged
    * header Authorization = authHeader
    Given path db + '/broken'
    And request { "invariants": [ { "name": "reachesOut", "stages": [ { "$lookup": { "from": "other", "localField": "a", "foreignField": "b", "as": "c" } } ] } ] }
    When method PUT
    Then assert responseStatus == 400

Scenario: An invariant that is disabled is not evaluated

    * header Authorization = authHeader
    Given path db + '/off'
    And request { "invariants": [ { "name": "impossible", "enabled": false, "stages": [ { "$match": { } } ] } ] }
    When method PUT
    Then assert responseStatus == 201

    # the rule would refuse everything if it were on: $match {} returns every document, and holdsWhen
    # defaults to empty
    * header Authorization = authHeader
    Given path db + '/off'
    And request { "_id": "x" }
    When method POST
    Then assert responseStatus == 201

Scenario: The guard document is taken on every write to a guarded collection

    # An internal, asserted deliberately: it is what makes an invariant a guarantee rather than a
    # hope. Two concurrent writes each valid on their own snapshot and invalid together would both
    # commit — snapshot isolation does not detect that — unless every write to the collection
    # contends one document. If this stopped being written, every other scenario here would still
    # pass and the guarantee would be gone.
    * header Authorization = authHeader
    Given path accounts
    And request { "_id": "dave", "balance": 5 }
    When method POST
    Then assert responseStatus == 201

    * header Authorization = authHeader
    Given path db + '/_invariants/accounts'
    When method GET
    Then assert responseStatus == 200
    And match response.v == '#number'
    And assert response.v > 0
