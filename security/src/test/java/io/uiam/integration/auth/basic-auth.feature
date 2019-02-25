Feature: test basic authentication mechanism

Background:
* url 'http://localhost:8080'
* def authHeader = 'Basic YWRtaW46Y2hhbmdlaXQ'
# YWRtaW46Y2hhbmdlaXQ= => admin:changeit
* def wrongAuthHeader = 'Basic YWRtaW46d3Jvbmc='
# YWRtaW46d3Jvbmc= => admin:wrong (wrong password)
* def basicAuthChallenge = 'Basic realm="uIAM Realm"'
* def identityEncoding = 'identity'

Scenario: request without Authorization header
    * header Accept-Encoding = 'identity'
    Given path '/secho'
    When method GET
    Then status 401
    * def challenge = responseHeaders['WWW-Authenticate'][0]
    And match challenge == basicAuthChallenge

Scenario: request with wrong Authorization header
    * header Authorization = wrongAuthHeader
    * header Accept-Encoding = identityEncoding
    Given path '/secho'
    When method GET
    Then status 401
    * def challenge = responseHeaders['WWW-Authenticate'][0]
    And match challenge == basicAuthChallenge

Scenario: request with valid Authorization header
    * header Authorization = authHeader
    * header Accept-Encoding = identityEncoding
    Given path '/secho'    
    When method GET
    Then status 200
    And match response.headers['X-Forwarded-Account-Roles'][0] == 'user'
    And match response.headers['X-Forwarded-Account-Roles'][1] == 'admin'