@ignore
# WARNING: with karate v1.4.1 these scenarios hang
# it seems the socket does not disconnect after the scenario completes....

# Workaround is forcing socket disconnection by RESTHeart. However if a test fails it hangs.

# Other workaroud is:

# 1) run restheart
# $ java -jar core/target/restheart.jar -o core/src/test/resources/etc/conf-overrides.yml

# 2) execute a single scenario
# $ cd core
# $ mvn test -Dtest=RunnerIT "-Dkarate.options=/Users/uji/development/restheart/core/src/test/java/karate/streams.feature:66"

# 3) let the scenario run. at the end it hangs...

# 4) stop restheart. the test result will be printed out

@parallel=false
Feature: Test Change Streams

Background:
* def authHeader = 'Basic YWRtaW46c2VjcmV0'
* url 'http://localhost:8080'
* def db = '/test-change-streams'
* def coll = db + '/coll'
* def anotherColl = db + '/anotherColl'
* call read('./streams-setup.feature')

* def sleep =
"""
function(seconds) {
    java.lang.Thread.sleep(seconds*1000)
}
"""

@requires-mongodb-3.6 @requires-replica-set
Scenario: Performing a simple GET request on a Change Stream resource (Expected 400 Bad Request)
    * header Authorization = authHeader
    Given path coll + '/_streams/changeStream'
    When method get
    Then status 400

@requires-mongodb-3.6 @requires-replica-set
Scenario: test insert (POST) new document (without avars)
    * def streamPath = '/_streams/changeStream'
    * def baseUrl = 'http://localhost:8080'
    * def host = baseUrl + encodeURI(coll + streamPath)
    * def socket = karate.webSocket(host, null, { headers: { Authorization: 'Basic YWRtaW46c2VjcmV0' }})
    * call sleep 3

    Given path coll
    And header Authorization = authHeader
    And request {"a":1, "b":2, "c":"test"}
    When method POST
    Then status 201

    * listen 5000
    * json result = listenResult
    * match result.operationType == 'insert'
    * match result.fullDocument.a == 1
    * match result.fullDocument.b == 2
    * match result.fullDocument.c == 'test'

    # this is a workaround to force socket to disconnet (thanks to obsoleteChangeStreamRemover)
    # otherwise the test hangs
    * header Authorization = authHeader
    Given path coll
    And request {"streams": [] }
    When method PUT
    Then status 200

@requires-mongodb-3.6 @requires-replica-set
Scenario: test insert (POST) new document (with avars)

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    * def streamPath = '/_streams/changeStreamWithStageParam?avars={\'param\': \'test\'}'
    * def baseUrl = 'http://localhost:8080'
    * def host = baseUrl + encodeURI(coll + streamPath)
    * def socket = karate.webSocket(host, null, { headers: { Authorization: 'Basic YWRtaW46c2VjcmV0' }})
    * call sleep 3

    # This POST shouldn't be notified
    * header Authorization = authHeader
    Given path coll
    And request {"anotherProp": 1}
    When method POST
    Then status 201

    * listen 5000
    * match listenResult == '#null'

    * header Authorization = authHeader
    Given path coll
    And request {"targettedProperty": "test", "anotherProp": 1}
    When method POST
    * listen 5000
    * json result = listenResult
    * match result.operationType == 'insert'
    * match result.fullDocument.targettedProperty == 'test'
    * match result.fullDocument.anotherProp == 1

    # this is a workaround to force socket to disconnet (thanks to obsoleteChangeStreamRemover)
    # otherwise the test hangs
    * header Authorization = authHeader
    Given path coll
    And request {"streams": [] }
    When method PUT
    Then status 200

@requires-mongodb-3.6 @requires-replica-set
Scenario: test PATCH on inserted document (without avars)

    * header Authorization = authHeader
    * def streamPath = '/_streams/changeStream'
    * def baseUrl = 'http://localhost:8080'
    * def host = baseUrl + encodeURI(coll + streamPath)

    Given path coll
    And request {"a":1, "b":2, "c":"test"}
    When method POST
    Then def postLocation = responseHeaders['Location'][0]
    Then status 201

    * def socket = karate.webSocket(host, null, { headers: { Authorization: 'Basic YWRtaW46c2VjcmV0' }})
    * call sleep 3

    * header Authorization = authHeader
    Given url postLocation
    And request {"moreProp": "test", "anotherProp": 1, "$unset": {"b":1}}
    When method PATCH

    * listen 5000
    * json parsedMsg = listenResult
    * print parsedMsg
    * match parsedMsg.operationType == 'update'
    * match parsedMsg.updateDescription.updatedFields.moreProp == 'test'
    * match parsedMsg.updateDescription.updatedFields.anotherProp == 1
    * match parsedMsg.updateDescription.removedFields contains "b"

    # this is a workaround to force socket to disconnet (thanks to obsoleteChangeStreamRemover)
    # otherwise the test hangs
    * header Authorization = authHeader
    Given url baseUrl
    Given path coll
    And request {"streams": [] }
    When method PUT
    Then status 200

@requires-mongodb-3.6 @requires-replica-set
Scenario: test PATCH on inserted document (with avars)

    * header Authorization = authHeader
    * def streamPath = '/_streams/changeStreamWithStageParam?avars={\'param\': \'test\'}'
    * def baseUrl = 'http://localhost:8080'
    * def host = baseUrl + encodeURI(coll + streamPath)

    Given path coll
    And request {"targettedProperty": "test", "toBeRemoved": null}
    When method POST
    Then def location = responseHeaders['Location'][0]
    Then status 201
    * print location

    * def socket = karate.webSocket(host, null, { headers: { Authorization: 'Basic YWRtaW46c2VjcmV0' }})
    * call sleep 3

    * header Authorization = authHeader
    Given url location
    And request {"moreProp": "test", "anotherProp": 1, "$unset": {"toBeRemoved":1}}
    When method PATCH
    Then status 200

    * listen 5000
    * json parsedMsg = listenResult
    * print parsedMsg
    * match parsedMsg.operationType == 'update'
    * match parsedMsg.updateDescription.updatedFields.moreProp == 'test'
    * match parsedMsg.updateDescription.updatedFields.anotherProp == 1
    * match parsedMsg.updateDescription.removedFields contains "toBeRemoved"

    # this is a workaround to force socket to disconnet (thanks to obsoleteChangeStreamRemover)
    # otherwise the test hangs
    * header Authorization = authHeader
    Given url baseUrl
    Given path coll
    And request {"streams": [] }
    When method PUT
    Then status 200

@requires-mongodb-3.6 @requires-replica-set
Scenario: test PUT upserting notifications (without avars)

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    * def streamPath = '/_streams/changeStream'
    * def baseUrl = 'http://localhost:8080'
    * def host = baseUrl + encodeURI(coll + streamPath)
    * def socket = karate.webSocket(host, null, { headers: { Authorization: 'Basic YWRtaW46c2VjcmV0' }})
    * call sleep 3

    * header Authorization = authHeader
    Given path coll + '/testput'
    And request {"a":1, "b":2, "c":"test"}
    And param wm = 'upsert'
    When method PUT
    Then status 201

    * listen 5000
    * json parsedInsertingMsg = listenResult
    And print parsedInsertingMsg
    * match parsedInsertingMsg.operationType == 'insert'
    * match parsedInsertingMsg.fullDocument.a == 1
    * match parsedInsertingMsg.fullDocument.b == 2
    * match parsedInsertingMsg.fullDocument.c == 'test'

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    * def streamPath = '/_streams/changeStream'
    * def baseUrl = 'http://localhost:8080'
    * def host = baseUrl + encodeURI(coll + streamPath)
    * def socket = karate.webSocket(host, null, { headers: { Authorization: 'Basic YWRtaW46c2VjcmV0' }})
    * call sleep 3

    * header Authorization = authHeader
    Given path coll + '/testput'
    And request {"moreProp": "test", "anotherProp": 1}
    And param wm = 'upsert'
    When method PUT
    Then status 200

    * listen 5000
    * json parsedEditMsg = listenResult
    * print parsedEditMsg
    * match parsedEditMsg.operationType == 'replace'

    # this is a workaround to force socket to disconnet (thanks to obsoleteChangeStreamRemover)
    # otherwise the test hangs
    * header Authorization = authHeader
    Given path coll
    And request {"streams": [] }
    When method PUT
    Then status 200


@requires-mongodb-3.6 @requires-replica-set
Scenario: test PUT upserting notifications (with avars)

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    * def streamPath = '/_streams/changeStreamWithStageParam?avars={\'param\': \'test\'}'
    * def baseUrl = 'http://localhost:8080'
    * def host = baseUrl + encodeURI(coll + streamPath)
    * def socket = karate.webSocket(host, null, { headers: { Authorization: 'Basic YWRtaW46c2VjcmV0' }})
    * call sleep 3

    * header Authorization = authHeader
    Given path coll + '/testputwithavars'
    And request {"targettedProperty": "test"}
    And param wm = 'upsert'
    When method PUT
    Then status 201

    * listen 5000
    * json parsedInsertingMsg = listenResult
    * print parsedInsertingMsg
    * match parsedInsertingMsg.operationType == 'insert'
    * match parsedInsertingMsg.fullDocument.targettedProperty == 'test'

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    * def streamPath = '/_streams/changeStream'
    * def baseUrl = 'http://localhost:8080'
    * def host = baseUrl + encodeURI(coll + streamPath)
    * def socket = karate.webSocket(host, null, { headers: { Authorization: 'Basic YWRtaW46c2VjcmV0' }})
    * call sleep 3

    * header Authorization = authHeader
    Given path coll + '/testputwithavars'
    And request {"moreProp": "test", "anotherProp": 1}
    And param wm = 'upsert'
    When method PUT
    Then status 200

    * listen 5000
    * json parsedEditMsg = listenResult
    * print parsedEditMsg
    * match parsedEditMsg.operationType == 'replace'

    # this is a workaround to force socket to disconnet (thanks to obsoleteChangeStreamRemover)
    # otherwise the test hangs
    * header Authorization = authHeader
    Given path coll
    And request {"streams": [] }
    When method PUT
    Then status 200

@requires-mongodb-4 @requires-replica-set
Scenario: https://github.com/SoftInstigate/restheart/issues/414

    # Establish WebSocket connection to get notified.
    Given def streamPath = '/_streams/testWrongStreamDefinition'
    And def baseUrl = 'http://localhost:8080'

    * header Authorization = authHeader
    * header Connection = 'Upgrade'
    * header Upgrade = 'websocket'
    Given path anotherColl + streamPath
    When method GET
    Then status 500

@requires-mongodb-4 @requires-replica-set
Scenario: https://github.com/SoftInstigate/restheart/issues/415

    # Establish WebSocket connection to get notified.
    * def streamPath = '/_streams/testResume'
    * def baseUrl = 'http://localhost:8080'
    * def host = baseUrl + encodeURI(coll + streamPath)
    * def firstSocket = karate.webSocket(host, null, { headers: { Authorization: 'Basic YWRtaW46c2VjcmV0' }})
    * call sleep 3

    * header Authorization = authHeader
    Given path coll
    And request { "n": 1 }
    When method POST
    Then status 201

    * listen 5000
    * json firstParsedMsg = listenResult
    * print firstParsedMsg
    * match firstParsedMsg.operationType == 'insert'
    * match firstParsedMsg.fullDocument.n == 1
    * def firstId = firstParsedMsg.fullDocument._id['$oid']

    * header Authorization = authHeader
    Given path coll
    # this POST will cause an error in the change stream leading to disconnect the socket
    And request { "n": "string" }
    When method POST
    Then status 201

    * listen 5000
    * match listenResult == '#null'

    * header Authorization = authHeader
    Given path coll
    And request { "n": 2 }
    When method POST
    Then status 201

    * listen 5000
    * match listenResult != '#null'
    * json thirdParsedMsg = listenResult
    * print thirdParsedMsg
    * match thirdParsedMsg.operationType == 'insert'
    * match thirdParsedMsg.fullDocument.div == 0.5

    # this is a workaround to force socket to disconnet (thanks to obsoleteChangeStreamRemover)
    # otherwise the test hangs
    * header Authorization = authHeader
    Given path coll
    And request {"streams": [] }
    When method PUT
    Then status 200