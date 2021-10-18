@ignore
@parallel=false
Feature: Test Change Streams

Background:
* def adminAuthHeader = 'Basic YWRtaW46c2VjcmV0'
* url 'http://localhost:8080'
* def db = '/test-change-streams'
* def coll = db + '/coll'
* def anotherColl = db + '/anotherColl'
* callonce read('./streams-setup.feature')
* def parseResponse =
"""
function(json) {
return JSON.parse(json)
}
"""
* def sleep =
"""
function(seconds){
    java.lang.Thread.sleep(seconds*1000);
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

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    Given def streamPath = '/_streams/changeStream'
    And def baseUrl = 'http://localhost:8080'
    And def handler = function(notification) { karate.signal(notification) }
    And def host = baseUrl + encodeURI(coll + streamPath)
    Then def socket = karate.webSocket(host, handler)
    
    * callonce sleep 3
    * header Authorization = authHeader
    Given path coll
    And request {"a":1, "b":2, "c":"test"}
    When method POST
    And def result = karate.listen(5000)
    Then def parsedMsg = parseResponse(result)
    * print parsedMsg
    And match parsedMsg.operationType == 'insert'
    And match parsedMsg.fullDocument.a == 1
    And match parsedMsg.fullDocument.b == 2
    And match parsedMsg.fullDocument.c == 'test'


@requires-mongodb-3.6 @requires-replica-set
Scenario: test insert (POST) new document (with avars)

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    Given def streamPath = '/_streams/changeStreamWithStageParam?avars={\'param\': \'test\'}'
    And def baseUrl = 'http://localhost:8080'
    And def handler = function(notification) { karate.signal(notification) }
    And def host = baseUrl + encodeURI(coll + streamPath)
    Then def socket = karate.webSocket(host, handler)
    
    # This POST shouldn't be notified
    * callonce sleep 3
    * header Authorization = authHeader
    Given path coll
    And request {"anotherProp": 1}
    When method POST
    And def firstPostResult = karate.listen(5000)
    Then match firstPostResult == '#null'
    
    * header Authorization = authHeader
    Given path coll
    And request {"targettedProperty": "test", "anotherProp": 1}
    When method POST
    And def result = karate.listen(5000)
    Then def parsedMsg = parseResponse(result)
    * print parsedMsg
    And match parsedMsg.operationType == 'insert'
    And match parsedMsg.fullDocument.targettedProperty == 'test'
    And match parsedMsg.fullDocument.anotherProp == 1
    
    
@requires-mongodb-3.6 @requires-replica-set
Scenario: test PATCH on inserted document (without avars)
    
    * header Authorization = authHeader
    Given path coll
    And request {"a":1, "b":2, "c":"test"}
    When method POST
    Then def postLocation = responseHeaders['Location'][0]

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    Given def streamPath = '/_streams/changeStream'
    And def baseUrl = 'http://localhost:8080'
    And def handler = function(notification) { karate.signal(notification) }
    And def host = baseUrl + encodeURI(coll + streamPath)
    Then def socket = karate.webSocket(host, handler)

    * callonce sleep 3
    * header Authorization = authHeader
    Given url postLocation
    And request {"moreProp": "test", "anotherProp": 1, "$unset": {"b":1}}
    When method PATCH
    And def result = karate.listen(5000)
    Then def parsedMsg = parseResponse(result)
    * print parsedMsg
    And match parsedMsg.operationType == 'update'
    And match parsedMsg.updateDescription.updatedFields.moreProp == 'test'
    And match parsedMsg.updateDescription.updatedFields.anotherProp == 1
    And match parsedMsg.updateDescription.removedFields contains "b"


@requires-mongodb-3.6 @requires-replica-set
Scenario: test PATCH on inserted document (with avars)
    
    * header Authorization = authHeader
    Given path coll
    And request {"targettedProperty": "test", "toBeRemoved": null}
    When method POST
    Then def location = responseHeaders['Location'][0]
    * print location

    * call sleep 3
    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    Given def streamPath = '/_streams/changeStreamWithStageParam?avars={\'param\': \'test\'}'
    And def baseUrl = 'http://localhost:8080'
    And def handler = function(notification) { karate.signal(notification) }
    And def host = baseUrl + encodeURI(coll + streamPath)
    Then def socket = karate.webSocket(host, handler)

    * callonce sleep 3
    * header Authorization = authHeader
    Given url location
    And request {"moreProp": "test", "anotherProp": 1, "$unset": {"toBeRemoved":1}}
    When method PATCH
    And def result = karate.listen(5000)
    Then def parsedMsg = parseResponse(result)
    * print parsedMsg
    And match parsedMsg.operationType == 'update'
    And match parsedMsg.updateDescription.updatedFields.moreProp == 'test'
    And match parsedMsg.updateDescription.updatedFields.anotherProp == 1
    And match parsedMsg.updateDescription.removedFields contains "toBeRemoved"


@requires-mongodb-3.6 @requires-replica-set
Scenario: test PUT upserting notifications (without avars)

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    Given def streamPath = '/_streams/changeStream'
    And def baseUrl = 'http://localhost:8080'
    And def handler = function(notification) { karate.signal(notification) }
    And def host = baseUrl + encodeURI(coll + streamPath)
    Then def socket = karate.webSocket(host, handler)

    * callonce sleep 3
    * header Authorization = authHeader
    Given path coll + '/testput'
    And request {"a":1, "b":2, "c":"test"}
    When method PUT
    And def firstResult = karate.listen(5000)
    Then def parsedInsertingMsg = parseResponse(firstResult)
    And print parsedInsertingMsg
    And match parsedInsertingMsg.operationType == 'insert'
    And match parsedInsertingMsg.fullDocument.a == 1
    And match parsedInsertingMsg.fullDocument.b == 2
    And match parsedInsertingMsg.fullDocument.c == 'test'

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    Given def streamPath = '/_streams/changeStream'
    And def baseUrl = 'http://localhost:8080'
    And def handler = function(notification) { karate.signal(notification) }
    And def host = baseUrl + encodeURI(coll + streamPath)
    Then def socket = karate.webSocket(host, handler)

    * callonce sleep 3
    * header Authorization = authHeader
    Given path coll + '/testput'
    And request {"moreProp": "test", "anotherProp": 1}
    When method PUT
    And def secondResult = karate.listen(5000)
    Then def parsedEditMsg = parseResponse(secondResult)
    And print parsedEditMsg
    And match parsedEditMsg.operationType == 'replace'


@requires-mongodb-3.6 @requires-replica-set
Scenario: test PUT upserting notifications (with avars)

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    Given def streamPath = '/_streams/changeStreamWithStageParam?avars={\'param\': \'test\'}'
    And def baseUrl = 'http://localhost:8080'
    And def handler = function(notification) { karate.signal(notification) }
    And def host = baseUrl + encodeURI(coll + streamPath)
    Then def socket = karate.webSocket(host, handler)

    * callonce sleep 3
    * header Authorization = authHeader
    Given path coll + '/testputwithavars'
    And request {"targettedProperty": "test"}
    When method PUT
    And def firstResult = karate.listen(5000)
    Then def parsedInsertingMsg = parseResponse(firstResult)
    And print parsedInsertingMsg
    And match parsedInsertingMsg.operationType == 'insert'
    And match parsedInsertingMsg.fullDocument.targettedProperty == 'test'

    # Establish WebSocket connection to get notified.
    * header Authorization = authHeader
    Given def streamPath = '/_streams/changeStream'
    And def baseUrl = 'http://localhost:8080'
    And def handler = function(notification) { karate.signal(notification) }
    And def host = baseUrl + encodeURI(coll + streamPath)
    Then def socket = karate.webSocket(host, handler)

    * callonce sleep 3
    * header Authorization = authHeader
    Given path coll + '/testputwithavars'
    And request {"moreProp": "test", "anotherProp": 1}
    When method PUT
    And def secondResult = karate.listen(5000)
    Then def parsedEditMsg = parseResponse(secondResult)
    And print parsedEditMsg
    And match parsedEditMsg.operationType == 'replace'


@requires-mongodb-3.6 @requires-replica-set
Scenario: https://github.com/SoftInstigate/restheart/issues/373

    # Connect to "cs" stream.
    * header Authorization = authHeader
    Given def streamPath = '/_streams/cs'
    And def baseUrl = 'http://localhost:8080'
    And def handler = function(notification) { karate.signal(notification) }
    And def host = baseUrl + encodeURI(coll + streamPath)
    Then def socket = karate.webSocket(host, handler)


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
    Given def streamPath = '/_streams/testResume'
    And def baseUrl = 'http://localhost:8080'

    And def host = baseUrl + encodeURI(coll + streamPath)
    Then def socket = karate.webSocket(host, handler)

    * call sleep 1
    * header Authorization = authHeader
    Given path coll
    And request { "n": 1 }
    When method POST
    And def firstResult = socket.listen(2000)
    * print firstResult
    Then def firstParsedMsg = parseResponse(firstResult)
    * print firstParsedMsg
    And match firstParsedMsg.operationType == 'insert'
    And match firstParsedMsg.fullDocument.n == 1
    
    * call sleep 1
    * header Authorization = authHeader
    Given path coll
    And request { "n": "string" }
    When method POST
    And def secondResult = socket.listen(2000)
    Then status 201
    And match secondResult == '#null'

    * call sleep 3
    * header Authorization = authHeader
    Given path coll
    And request { "n": 1 }
    When method POST
    And def thirdResult = socket.listen(2000)
    * print thirdResult
    Then def thirdParsedMsg = parseResponse(thirdResult)
    * print thirdParsedMsg
    And match thirdParsedMsg.operationType == 'insert'
    And match thirdParsedMsg.fullDocument.n == 1