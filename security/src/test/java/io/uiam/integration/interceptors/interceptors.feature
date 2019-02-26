Feature: test interceptors

Background:
* url 'http://localhost:8080'
* def authHeader = 'Basic YWRtaW46Y2hhbmdlaXQ'
# YWRtaW46Y2hhbmdlaXQ= => admin:changeit
* def identityEncoding = 'identity'
* def someJson = { s: "string", n: 1, obj: { x:1, y:2}, array:["a","b","c"] }

Scenario: POST /iecho { n: 1, s: "test" } with request and response interceptors
    Given path '/iecho'
    And header Authorization = authHeader
    And header Accept-Encoding = identityEncoding
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
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /iecho'

    Scenario: POST /siecho { n: 1, s: "test" } with request and response interceptors
    Given path '/siecho'
    And header Authorization = authHeader
    And header Accept-Encoding = identityEncoding
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
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /iecho'
    And match responseHeaders['header'][1] == 'added by EchoExampleResponseInterceptor /siecho'

    Scenario: POST /iecho n=1&s=test with request and response interceptors
    Given path '/iecho'
    And header Authorization = authHeader
    And header Accept-Encoding = identityEncoding
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
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /iecho'

    Scenario: POST /siecho n=1&s=test with request and response interceptors
    Given path '/siecho'
    And header Authorization = authHeader
    And header Accept-Encoding = identityEncoding
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
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /iecho'
    And match responseHeaders['header'][1] == 'added by EchoExampleResponseInterceptor /siecho'

    Scenario: multipart POST /iecho json=(big.json) file=RESTHeart.pdf with request and response interceptors
    Given path '/iecho'
    And header Authorization = authHeader
    And header Accept-Encoding = identityEncoding
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
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /iecho'
    And match response.note == 'showing up to 20 bytes of the request content'

    Scenario: multipart POST /siecho json=(big.json) file=RESTHeart.pdf with request and response interceptors
    Given path '/siecho'
    And header Authorization = authHeader
    And header Accept-Encoding = identityEncoding
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
    And match responseHeaders['header'][0] == 'added by EchoExampleResponseInterceptor /iecho'
    And match responseHeaders['header'][1] == 'added by EchoExampleResponseInterceptor /siecho'
    And match response.note == 'showing up to 20 bytes of the request content'

    Scenario: POST /echo with Accept-Encoding=identity
    Given path '/echo' 
    And header Accept-Encoding = identityEncoding
    And request { n: 1 }
    When method POST
    Then status 200
    And match responseHeaders['Content-Encoding'][0] == 'identity'

    Scenario: POST /secho with Accept-Encoding=identity
    Given path '/secho'
    And header Authorization = authHeader
    And header Accept-Encoding = identityEncoding
    And request { n: 1 }
    When method POST
    Then status 200
    And match responseHeaders['Content-Encoding'][0] == 'identity'

    Scenario: POST /echo with Accept-Encoding=gzip
    Given path '/echo'
    And header Accept-Encoding = 'gzip'
    And request { n: 1 }
    When method PATCH
    Then status 200
    #And match responseHeaders['Content-Encoding'][0] == 'gzip'
    # when response is compressed apache http decompress it and remove the Content-Encoding header

    Scenario: POST /secho with Accept-Encoding=gzip
    Given path '/secho'
    And header Authorization = authHeader
    And header Accept-Encoding = 'gzip'
    And request { n: 1 }
    When method POST
    Then status 200
    #And match responseHeaders['Content-Encoding'][0] == 'deflate'
    # when response is compressed apache http decompress it and remove the Content-Encoding header

    Scenario: POST /echo with Accept-Encoding=deflate
    Given path '/echo'
    And header Accept-Encoding = 'deflate'
    And request { n: 1 }
    When method POST
    Then status 200
    #And match responseHeaders['Content-Encoding'][0] == 'deflate'
    # when response is compressed apache http decompress it and remove the Content-Encoding header

    Scenario: POST /secho with Accept-Encoding=deflate
    Given path '/secho'
    And header Authorization = authHeader
    And header Accept-Encoding = 'deflate'
    And request { n: 1 }
    When method POST
    Then status 200
    #And match responseHeaders['Content-Encoding'][0] == 'deflate'
    # when response is compressed apache http decompress it and remove the Content-Encoding header

    Scenario: POST /iecho with Accept-Encoding=identity
    Given path '/iecho'
    And header Accept-Encoding = 'identity'
    And request { n: 1 }
    When method POST
    Then status 200
    And match responseHeaders['Content-Encoding'][0] == 'identity'

    Scenario: POST /iecho with Accept-Encoding=gzip, get identity anyway since response interceptors are involved
    Given path '/iecho'
    And header Authorization = authHeader
    And header Accept-Encoding = 'gzip'
    And request { n: 1 }
    When method POST
    Then status 200
    And match responseHeaders['Content-Encoding'][0] == 'identity'

    Scenario: POST /iecho with Accept-Encoding=deflate, get identity anyway since response interceptors are involved
    Given path '/iecho'
    And header Accept-Encoding = 'deflate'
    And request { n: 1 }
    When method POST
    Then status 200
    And match responseHeaders['Content-Encoding'][0] == 'identity'

    Scenario: POST /siecho with Accept-Encoding=identity
    Given path '/siecho'
    And header Authorization = authHeader
    And header Accept-Encoding = 'identity'
    And request { n: 1 }
    When method POST
    Then status 200
    And match responseHeaders['Content-Encoding'][0] == 'identity'

    Scenario: POST /siecho with Accept-Encoding=gzip, get identity anyway since response interceptors are involved
    Given path '/siecho'
    And header Authorization = authHeader
    And header Accept-Encoding = 'gzip'
    And request { n: 1 }
    When method POST
    Then status 200
    And match responseHeaders['Content-Encoding'][0] == 'identity'

    Scenario: POST /siecho with Accept-Encoding=deflate, get identity anyway since response interceptors are involved
    Given path '/siecho'
    And header Authorization = authHeader
    And header Accept-Encoding = 'deflate'
    And request { n: 1 }
    When method POST
    Then status 200
    And match responseHeaders['Content-Encoding'][0] == 'identity'
    