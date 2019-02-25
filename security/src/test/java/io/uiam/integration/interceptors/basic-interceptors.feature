Feature: test interceptors

Background:
* url 'http://localhost:8080'
* def authHeader = 'Basic YWRtaW46Y2hhbmdlaXQ'
# YWRtaW46Y2hhbmdlaXQ= => admin:changeit
* def identityEncoding = 'identity'
* def someJson = { s: "string", n: 1, obj: { x:1, y:2}, array:["a","b","c"] }

Scenario: POST /echo { n: 1, s: "test" } with request and response interceptors
    * header Authorization = authHeader
    * header Accept-Encoding = identityEncoding
    Given path '/echo'
    And request { n: 1, s: "test" }
    And param key1 = 'key1'
    And param key2 = 'key2'
    And header nheader = 'header'
    When method POST
    Then status 200
    And match response.content.n == 1
    And match response.content.s == 'test'
    And match response.content.prop1 == 'property added by EchoExampleRequestInterceptor'
    And match response.prop2 == 'property added by EchoExampleResponseInterceptor'
    And match response.qparams.param == [ "param added by EchoExampleRequestInterceptor"]
    And match response.qparams == { key1:["key1"], key2:["key2"], param:["param added by EchoExampleRequestInterceptor"]} }
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /echo'

    Scenario: POST /secho { n: 1, s: "test" } with request and response interceptors
    * header Authorization = authHeader
    * header Accept-Encoding = identityEncoding
    Given path '/secho'
    And request { n: 1, s: "test" }
    And param key1 = 'key1'
    And param key2 = 'key2'
    And header nheader = 'header'
    When method POST
    Then status 200
    And match response.content.n == 1
    And match response.content.s == 'test'
    And match response.content.prop1 == 'property added by EchoExampleRequestInterceptor'
    And match response.prop2 == 'property added by EchoExampleResponseInterceptor'
    And match response.qparams.param == [ "param added by EchoExampleRequestInterceptor"]
    And match response.qparams == { key1:["key1"], key2:["key2"], param:["param added by EchoExampleRequestInterceptor"]} }
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /echo'
    And match responseHeaders['header'][1] == 'added by EchoExampleResponseInterceptor /secho'

    Scenario: POST /echo n=1&s=test with request and response interceptors
    * header Authorization = authHeader
    * header Accept-Encoding = identityEncoding
    Given path '/echo'
    And request { n: 1, s: "test" }
    And form field n = '1'
    And form field s = 'test'
    And param key1 = 'key1'
    And param key2 = 'key2'
    And header nheader = 'header'
    When method POST
    Then status 200
    And match response.content == 'n=1&s=test'
    And match response.prop2 == 'property added by EchoExampleResponseInterceptor'
    And match response.qparams.param == [ "param added by EchoExampleRequestInterceptor"]
    And match response.qparams == { key1:["key1"], key2:["key2"], param:["param added by EchoExampleRequestInterceptor"]} }
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /echo'

    Scenario: POST /secho n=1&s=test with request and response interceptors
    * header Authorization = authHeader
    * header Accept-Encoding = identityEncoding
    Given path '/secho'
    And form field n = '1'
    And form field s = 'test'
    And param key1 = 'key1'
    And param key2 = 'key2'
    And header nheader = 'header'
    When method POST
    Then status 200
    And match response.content == 'n=1&s=test'
    And match response.prop2 == 'property added by EchoExampleResponseInterceptor'
    And match response.qparams.param == [ "param added by EchoExampleRequestInterceptor"]
    And match response.qparams == { key1:["key1"], key2:["key2"], param:["param added by EchoExampleRequestInterceptor"]} }
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /echo'
    And match responseHeaders['header'][1] == 'added by EchoExampleResponseInterceptor /secho'

    Scenario: multipar POST /echo json=(big.json) file=RESTHeart.pdf with request and response interceptors
    * header Authorization = authHeader
    * header Accept-Encoding = identityEncoding
    Given path '/echo'
    And multipart field json = someJson
    And multipart field file = read('RESTHeart.pdf')
    And param key1 = 'key1'
    And param key2 = 'key2'
    And header nheader = 'header'
    When method POST
    Then status 200
    And match response.prop2 == 'property added by EchoExampleResponseInterceptor'
    And match response.qparams.param == [ "param added by EchoExampleRequestInterceptor"]
    And match response.qparams == { key1:["key1"], key2:["key2"], param:["param added by EchoExampleRequestInterceptor"]} }
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /echo'
    And match response.note == 'showing up to 20 bytes of the request content'

    Scenario: multipar POST /secho json=(big.json) file=RESTHeart.pdf with request and response interceptors
    * header Authorization = authHeader
    * header Accept-Encoding = identityEncoding
    Given path '/secho'
    And multipart field json = someJson
    And multipart field file = read('RESTHeart.pdf')
    And param key1 = 'key1'
    And param key2 = 'key2'
    And header nheader = 'header'
    When method POST
    Then status 200
    And match response.prop2 == 'property added by EchoExampleResponseInterceptor'
    And match response.qparams.param == [ "param added by EchoExampleRequestInterceptor"]
    And match response.qparams == { key1:["key1"], key2:["key2"], param:["param added by EchoExampleRequestInterceptor"]} }
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /echo'
    And match responseHeaders['header'][1] == 'added by EchoExampleResponseInterceptor /secho'
    And match response.note == 'showing up to 20 bytes of the request content'
    