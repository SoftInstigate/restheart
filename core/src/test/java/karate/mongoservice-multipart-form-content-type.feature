Feature: Test MongoService support for multipart and form content type

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

Scenario: Create test db and collection
    * header Authorization = authHeader
    Given path '/test-multipart-form'
    And request {}
    When method PUT
    Then status 201

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll'
    And request {}
    When method PUT
    Then status 201

Scenario: Test multipart/form-data POST document

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll'
    And multipart fields { _id:'mpdoc', s: 'foo', n: '1', ns: '"1"', b: 'true', bs: '"true"', json: '{ foo: "bar" }' }
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll/mpdoc'
    When method GET
    Then status 200
    And match response.s == 'foo'
    And match response.n == 1
    And match response.ns == '1'
    And match response.b == true
    And match response.bs == 'true'
    And match response.json == { foo: 'bar' }

Scenario: Test multipart/form-data PATCH with update operator $inc

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll/mpdoc'
    And multipart fields { $inc: '{ n: 100 }' }
    When method PATCH
    Then status 200

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll/mpdoc'
    When method GET
    Then status 200
    And match response.s == 'foo'
    And match response.n == 101
    And match response.ns == '1'
    And match response.b == true
    And match response.bs == 'true'
    And match response.json == { foo: 'bar' }

Scenario: Test multipart/form-data POST document with unquoted string

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll'
    And multipart fields { _id: 'mpunquoted', unquoted: 'foo', quoted: '"foo"' }
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll/mpunquoted'
    When method GET
    Then status 200
    And match response.unquoted == 'foo'
    And match response.quoted == 'foo'

Scenario: Test bad multipart/form-data POST document

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll'
    And multipart fields { wronjson: '{ foo: "bar"' }
    When method POST
    Then status 406

Scenario: Test application/x-www-form-urlencoded POST document

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll'
    And form field _id = 'fuedoc'
    And form field s = 'foo'
    And form field n = '1'
    And form field ns = '"1"'
    And form field b = 'true'
    And form field bs = '"true"'
    And form field json = '{ foo: "bar" }'
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll/fuedoc'
    When method GET
    Then status 200
    And match response.s == 'foo'
    And match response.n == 1
    And match response.ns == '1'
    And match response.b == true
    And match response.bs == 'true'
    And match response.json == { foo: 'bar' }

  Scenario: Test application/x-www-form-urlencoded PATCH with update operator $inc
    * header Authorization = authHeader
    Given path '/test-multipart-form/coll/fuedoc'
    And form field $inc = '{ n: 100 }'
    When method PATCH
    Then status 200

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll/fuedoc'
    When method GET
    Then status 200
    And match response.s == 'foo'
    And match response.n == 101
    And match response.ns == '1'
    And match response.b == true
    And match response.bs == 'true'
    And match response.json == { foo: 'bar' }

Scenario: Test application/x-www-form-urlencoded POST document

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll'
    And form field wrongjson = '{ foo: "bar }'
    When method POST
    Then status 406

Scenario: Test application/x-www-form-urlencoded POST document with unquoted string

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll'
    And form field _id = 'fueunquoted'
    And form field unquoted = 'foo'
    And form field quoted = '"foo"'
    When method POST
    Then status 201

    * header Authorization = authHeader
    Given path '/test-multipart-form/coll/fueunquoted'
    When method GET
    Then status 200
    And match response.unquoted == 'foo'
    And match response.quoted == 'foo'