Feature: test conflict error handling

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
* def authHeader = basic({username: 'admin', password: 'secret' })
* def idFromlocation = function(url) { return url.substring(url.length-24); }

Scenario: Create test data
    * header Authorization = authHeader
    Given path '/test-conflict-on-patch'
    And request {}
    When method PUT
    Then status 201

    * header Authorization = authHeader
    Given path '/test-conflict-on-patch/coll'
    And request {}
    When method PUT
    Then status 201

    * header Authorization = authHeader
    Given path '/test-conflict-on-patch/coll/_indexes/ids_code'
    And request { keys: { ids.code: 1 }, ops: {unique:true, partialFilterExpression: { ids.code: { $exists: true }} }}
    When method PUT
    Then status 201

    * header Authorization = authHeader
    Given path '/test-conflict-on-patch/coll'
    And request [ { _id: "doc1", ids: [ { code: "A" } ] }, { _id: "doc2", ids: [ { code: "B" } ] }, { _id: "doc3", ids: [ { foo: bar } ] } ]
    When method POST
    Then status 200

# **************** INSERT ****************

Scenario: Patch the document with index
    # get doc1 and save its etag
    * header Authorization = authHeader
    Given path '/test-conflict-on-patch/coll/doc1'
    When method GET
    Then status 200
    * print response
    And match response.ids[0] == { code: "A" }
    * def etag = response._etag.$oid
    * print etag

    # try to update doc1 with existing ids
    * header Authorization = authHeader
    Given path '/test-conflict-on-patch/coll/doc1'
    And request { "$set": { ids: [ { code: "B" } ] } }
    When method PATCH
    Then status 409

    # try to update doc1 with existing ids and passing the If-Match header
    * header Authorization = authHeader
    * header If-Match = etag
    Given path '/test-conflict-on-patch/coll/doc1'
    And request { "$set": { ids: [ { code: "B" } ] } }
    When method PATCH
    Then status 409

    # update doc1 with id without code
    * header Authorization = authHeader
    Given path '/test-conflict-on-patch/coll/doc1'
    And request { "$set": { ids: [ { code: "C" }, { foo: true } ] } }
    When method PATCH
    Then status 200

    # check the doc1 is correctly updated
    * header Authorization = authHeader
    Given path '/test-conflict-on-patch/coll/doc1'
    When method GET
    Then status 200
    * print response
    And match response.ids[0] == { code: "C" }
    And match response.ids[1] == { foo: true }
