Feature: test basic authentication mechanism

Background:
* url 'http://localhost:8080'
* def authHeader = 'Basic YWRtaW46c2VjcmV0'
# YWRtaW46Y2hhbmdlaXQ= => admin:secret
* def wrongAuthHeader = 'Basic YWRtaW46d3Jvbmc='
# YWRtaW46d3Jvbmc= => admin:wrong (wrong password)
* def basicAuthChallenge = 'Basic realm="uIAM Realm"'

Scenario: request without Authorization header
    * header Accept-Encoding = 'identity'
    Given path '/secho'
    When method GET
    Then status 401
    * def challenge = responseHeaders['WWW-Authenticate'][0]
    And match challenge == basicAuthChallenge

Scenario: request with wrong Authorization header
    * header Authorization = wrongAuthHeader
    Given path '/secho'
    When method GET
    Then status 401
    * def challenge = responseHeaders['WWW-Authenticate'][0]
    And match challenge == basicAuthChallenge

Scenario: request with valid Authorization header
    * header Authorization = authHeader
    Given path '/secho'    
    When method GET
    Then status 200
    And match response.headers['X-Forwarded-Account-Roles'][0] == 'user'
    And match response.headers['X-Forwarded-Account-Roles'][1] == 'admin'