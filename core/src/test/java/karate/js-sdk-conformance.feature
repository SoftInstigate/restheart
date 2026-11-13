@requires-graalvm
Feature: Conformance suite of the polyglot plugin SDK (restheart:sdk)

    Each scenario names a reference plugin, deployed from polyglot/src/test/resources/test-js-plugins,
    and asserts its HTTP response. The assertions are about the SDK, not about JavaScript: a second
    guest language ships the same reference plugins in its own directory, calls ensurePlugins with
    that directory and reuses every scenario below.

Background:
    * url 'http://localhost:8080'

    # the reference plugins by name, the same in every guest language. sdkUnavailable is missing:
    # it is never deployed, see its scenario
    * def referencePlugins =
    """
    [
        'sdkRequest', 'sdkEcho', 'sdkContentString', 'sdkContentJson', 'sdkContentBson', 'sdkContentBytes',
        'sdkErrors', 'sdkWebhook', 'sdkEncode', 'sdkResource', 'sdkLogger', 'sdkConfig', 'sdkHttp', 'sdkMongo',
        'sdkProviders', 'sdkStringInterceptor', 'sdkJsonInterceptor', 'sdkMongoInterceptor', 'sdkCsvInterceptor'
    ]
    """

    # copies the directory once and waits until RESTHeart has registered every reference plugin
    * def ensurePlugins =
    """
    function(dir) {
        var CopyFolderRecursively = Java.type('karate.CopyFolderRecursively');
        new CopyFolderRecursively().ensure(dir, referencePlugins);
    }
    """

    * def basic =
    """
    function(creds) {
        var temp = creds.username + ':' + creds.password;
        var Base64 = Java.type('java.util.Base64');
        var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
        return 'Basic ' + encoded;
    }
    """

    * def hmacSha256 =
    """
    function(secret, data) {
        var Mac = Java.type('javax.crypto.Mac');
        var SecretKeySpec = Java.type('javax.crypto.spec.SecretKeySpec');
        var HexFormat = Java.type('java.util.HexFormat');
        var mac = Mac.getInstance('HmacSHA256');
        mac.init(new SecretKeySpec(secret.getBytes('UTF-8'), 'HmacSHA256'));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes('UTF-8')));
    }
    """

    * def admin = basic({username: 'admin', password: 'secret'})
    * call ensurePlugins 'test-js-plugins'

# ---- request

Scenario: sdkRequest: path, method, query, queryAll, headers (case-insensitive), account, pathParams
    * header Authorization = admin
    * header X-Test = 'hello'
    Given path '/sdk/request'
    And param limit = '10'
    And param tag = ['a', 'b']
    And request { user: { name: 'alice' } }
    When method POST
    Then status 200
    And match response.path == '/sdk/request'
    And match response.method == 'POST'
    And match response.isGet == false
    And match response.isPost == true
    And match response.query == { limit: '10', missing: 'undefined', hasLimit: true, keys: '#array' }
    And match response.query.keys contains 'limit'
    And match response.queryAll.tag == ['a', 'b']
    And match response.headers == { lower: 'hello', upper: 'hello', missing: 'undefined' }
    And match response.account.name == 'admin'
    And match response.account.roles contains 'admin'
    And match response.authenticated == true
    And match response.pathParams == { what: 'request' }
    And match response.content == { user: { name: 'alice' } }

# ---- content kinds

Scenario: sdkContentString: contentKind string, req.content is a string, res.text()
    Given path '/sdk/content/string'
    And header Content-Type = 'text/plain'
    And request 'hello'
    When method POST
    Then status 200
    And match header Content-Type contains 'text/plain'
    And match response == 'string:HELLO!'
    And match header X-Intercepted == 'string'

Scenario: sdkContentJson: contentKind json, req.content is native data, res.json(), and a json interceptor on the response
    Given path '/sdk/content/json'
    And request { user: { name: 'alice' }, tags: ['x', 'y'], n: 3 }
    When method POST
    Then status 200
    And match response.name == 'alice'
    And match response.tags == ['X', 'Y']
    And match response.n == 3
    And match response.keys == ['user', 'tags', 'n']
    And match response.spread == { user: { name: 'alice' }, tags: ['x', 'y'], n: 3 }
    And match response.intercepted == true
    And match response.statusSeen == 200

Scenario: sdkContentBson: contentKind bson, extended JSON symmetric on read and write, bson.oid()
    Given path '/sdk/content/bson'
    And request { id: { $oid: '507f1f77bcf86cd799439011' }, when: { $date: '2026-01-01T00:00:00Z' }, n: 1, nested: { deep: { value: 'v' } } }
    When method POST
    Then status 200
    And match response.oid == '507f1f77bcf86cd799439011'
    And match response.isOid == true
    And match response.whenIsObject == true
    # a bson service answers in RESTHeart's default representation: dates as { $date: millis }, int64 plain
    And match response.when == { $date: 1767225600000 }
    And match response.n == 1
    And match response.nType == 'number'
    # $numberLong makes an int64 (written 4000000000); without it the same value is a double (written 4.0E9)
    And match response.big == 4000000000
    And match response.dbl == 4.0E9
    And match response.date == { $date: 0 }
    And match response.nested == 'v'
    And match response.fresh == 'string'

Scenario: sdkContentBytes: contentKind bytes, req.content is bytes, res.bytes()
    Given path '/sdk/content/bytes'
    And header Content-Type = 'application/octet-stream'
    And request 'ciao'
    When method POST
    Then status 200
    And match header Content-Type contains 'application/octet-stream'
    # a binary content type makes karate expose the body as bytes
    * def Str = Java.type('java.lang.String')
    * def text = new Str(responseBytes, 'UTF-8')
    And match text == '4:ciao'

# ---- errors

Scenario Outline: sdkErrors: a typed error is answered with its status and message (<kind>)
    Given path '/sdk/errors'
    And param kind = '<kind>'
    When method GET
    Then status <status>
    And match response.msg == '<msg>'

    Examples:
      | kind         | status | msg                  |
      | bad          | 400    | wrong limit qparam   |
      | unauthorized | 401    | Unauthorized         |
      | forbidden    | 403    | not yours            |
      | notfound     | 404    | no such thing        |
      | conflict     | 409    | already there        |
      | teapot       | 418    | short and stout      |
      | setInError   | 422    | set by setInError    |

Scenario: sdkErrors: an uncaught exception is a 500 and the client sees nothing of it
    Given path '/sdk/errors'
    And param kind = 'boom'
    When method GET
    Then status 500
    And match response == { msg: 'Internal Server Error' }

Scenario: sdkErrors: a TypeError in the plugin is a 500 too
    Given path '/sdk/errors'
    And param kind = 'typeerror'
    When method GET
    Then status 500
    And match response.msg == 'Internal Server Error'

Scenario: sdkErrors: no error
    Given path '/sdk/errors'
    When method GET
    Then status 200
    And match response == { ok: true }

# ---- utilities

Scenario: sdkWebhook: an HMAC-signed payload verified with crypto.hmacSha256 and crypto.timingSafeEqual
    * def payload = '{"event":"paid","amount":42}'
    Given path '/sdk/webhook'
    And header Content-Type = 'application/json'
    And header X-Signature = 'sha256=' + hmacSha256('it-webhook-secret', payload)
    And request payload
    When method POST
    Then status 200
    And match response.verified == true
    And match response.sha256 == '#regex [0-9a-f]{64}'
    And match response.uuidLength == 36
    And match response.randomHex == 16
    And match response.randomBase64 == 4

Scenario: sdkWebhook: a wrong signature is 401
    Given path '/sdk/webhook'
    And header Content-Type = 'application/json'
    And header X-Signature = 'sha256=' + hmacSha256('wrong-secret', '{}')
    And request '{}'
    When method POST
    Then status 401
    And match response.msg == 'bad signature'

Scenario: sdkEncode: base64, base64url, hex, utf8 and url encoding, each with its inverse
    Given path '/sdk/encode'
    And param s = 'ciao è a?&b'
    When method GET
    Then status 200
    And match response.base64 == 'Y2lhbyDDqCBhPyZi'
    And match response.base64Roundtrip == 'ciao è a?&b'
    And match response.base64url == 'Y2lhbyDDqCBhPyZi'
    And match response.base64urlRoundtrip == 'ciao è a?&b'
    And match response.hex == '6369616f20c3a820613f2662'
    And match response.hexRoundtrip == 'ciao è a?&b'
    And match response.utf8Length == 12
    And match response.urlEncoded == 'ciao+%C3%A8+a%3F%26b'
    And match response.urlRoundtrip == 'ciao è a?&b'

Scenario: sdkResource: reads a file next to the plugin and nothing outside the plugin directory
    Given path '/sdk/resource'
    When method GET
    Then status 200
    And match response == { text: 'ciao from a resource file', bytes: 26, exists: true, missing: false, outside: 'refused' }

Scenario: sdkLogger: null, undefined, objects and arrays are logged, nothing throws
    Given path '/sdk/logger'
    When method GET
    Then status 200
    And match response == { logged: true, debugEnabled: true }

# ---- capabilities

Scenario: sdkConfig: the plugin's section of the configuration, by plugin name
    Given path '/sdk/config'
    When method GET
    Then status 200
    And match response == { greeting: 'ciao', nested: 42, list: ['one', 'two'], missing: 'undefined', legacyPluginArgs: 'ciao' }

Scenario: sdkHttp: get, post, form, auth, a 4xx with ok === false, against a local endpoint
    Given path '/sdk/http'
    When method GET
    Then status 200
    And match response.get == { status: 200, ok: true, x: '1', header: 'yes', contentType: '#regex application/json.*' }
    And match response.post == { status: 200, content: { hello: 'world' }, contentType: 'application/json' }
    And match response.form == { status: 200, body: 'string:A=1&B=X+Y!' }
    And match response.auth == 'Basic YWRtaW46c2VjcmV0'
    And match response.notFound == { status: 404, ok: false }
    And match response.error == { status: 400, ok: false, msg: 'wrong limit qparam' }

Scenario: sdkMongo: reads, writes, cursors, extended JSON and command()
    Given path '/sdk/mongo'
    When method GET
    Then status 200
    And match response.insertedCount == 3
    And match response.insertedIdIsOid == true
    And match response.byId == 'd'
    And match response.byOidLiteral == 'd'
    And match response.sorted == ['d', 'c']
    And match response.iterated == 10
    And match response.spread == ['a', 'b', 'c', 'd']
    And match response.updated == { matched: 1, modified: 1 }
    And match response.upsertedIdIsOid == true
    And match response.replaced == 1
    And match response.aggTotal == 129
    And match response.bulk == { inserted: 1, modified: 1, deleted: 1 }
    And match response.distinct == ['a', 'b', 'e']
    And match response.count == 3
    And match response.cBig == { $numberLong: '9007199254740993' }
    And match response.cDec == { $numberDecimal: '1.50' }
    And match response.aWhen == { $date: '1970-01-01T00:00:00Z' }
    And match response.aTags == ['x', 'y']
    And match response.ping == 1
    And match response.index == 'by_name'
    And match response.deleted == 2
    And match response.rawIsClient == true

Scenario: sdkProviders: any registered provider by name
    Given path '/sdk/providers'
    When method GET
    Then status 200
    And match response == { hasMclient: true, hasRhConfig: true, hasHttpClient: true, rhConfigIsObject: true, missing: true }

Scenario: sdkUnavailable: a plugin importing what the sdk does not provide is not deployed
    # the import fails to link at load time and the service is never bound to its uri. The same
    # happens to a capability the deployment lacks (every one is available here, emails is enabled
    # in the integration tests): the log names the capability and why it is missing
    # with the service missing the path falls to the MongoDB service, where the db 'sdk' does not exist
    * header Authorization = admin
    Given path '/sdk/unavailable'
    When method GET
    Then status 404

# ---- interceptors on the MongoDB service and the csv loader

Scenario: sdkMongoInterceptor: req.mongo on GET /test-sdk/icoll/{id}, res.content as native data
    * header Authorization = admin
    Given path '/test-sdk/icoll'
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = admin
    Given path '/test-sdk/icoll/doc1'
    And param wm = 'upsert'
    And request { a: 1 }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = admin
    Given path '/test-sdk/icoll/doc1'
    When method GET
    Then status 200
    And match response._id == 'doc1'
    And match response.a == 1
    And match response.injected == { db: 'test-sdk', coll: 'icoll', id: 'doc1', isDocument: true, isCollection: false, page: 1, pagesize: 100 }

    * header Authorization = admin
    Given path '/test-sdk/icoll'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'
    And match response[0].injected == '#notpresent'

Scenario: sdkCsvInterceptor: req.content of the csv loader as an array of documents
    * header Authorization = admin
    Given path '/test-sdk/csv'
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = admin
    Given path '/csv'
    And param db = 'test-sdk'
    And param coll = 'csv'
    And param id = 0
    And header Content-Type = 'text/csv'
    And request 'id,name\n1,one\n2,two'
    When method POST
    Then assert responseStatus == 200 || responseStatus == 201

    * header Authorization = admin
    Given path '/test-sdk/csv'
    And param sort = '{"_id": 1}'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[2]'
    And match each response contains { imported: true }
    And match response[0].name == 'one'
