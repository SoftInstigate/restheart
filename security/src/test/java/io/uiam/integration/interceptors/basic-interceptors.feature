Feature: test interceptors

Background:
* url 'http://localhost:8080'
* def authHeader = 'Basic YWRtaW46Y2hhbmdlaXQ'
* def identityEncoding = 'identity'
# YWRtaW46Y2hhbmdlaXQ= => admin:changeit

Scenario: request /echo
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
    And match response.qparams == { key1:["key1"], key2:["key2"], param:["param added by EchoExampleRequestInterceptor"]} }
    And match responseHeaders['header'] == ['added by EchoExampleResponseInterceptor /echo']