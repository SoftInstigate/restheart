Feature: test jwt authentication mechanism

Background:
* url 'http://localhost:8080'
* def authHeader = 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJyZXN0aGVhcnQub3JnIiwicm9sZXMiOlsiYWRtaW4iXSwiZXh0cmEiOnsiYSI6MSwiYiI6Mn19.vDaJOoPH5EnAiyM6lF737vqgi978S2GIAQe1gq33eDU'
* def authHeaderMissingExtraProps = 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJyZXN0aGVhcnQub3JnIiwicm9sZXMiOlsiYWRtaW4iXX0.vA0oyHnow8iq998HQbQMe44AO35bXBnEFZNPnOXb0V4'
* def wrongAuthHeader = 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJiYWQtaXNzdWVyIiwicm9sZXMiOlsiYWRtaW4iXX0.-BYpDuGyKlq6Azz8AXbLkYmVepNE_i8CyD4vbvIJI5o'
# jwt are alog=HS256 and key=secret

Scenario: request without Authorization header
    Given path '/secho'
    When method GET
    Then status 401

Scenario: request with wrong Authorization header
    * header Authorization = wrongAuthHeader
    Given path '/secho'
    When method GET
    Then status 401

Scenario: request with valid Authorization header
    * header Authorization = authHeader
    Given path '/secho'
    When method GET
    Then status 200

Scenario: request with valid Authorization header but missing the extra properties
    * header Authorization = authHeaderMissingExtraProps
    Given path '/secho'
    When method GET
    Then status 401