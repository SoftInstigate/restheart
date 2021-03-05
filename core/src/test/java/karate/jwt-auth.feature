Feature: test jwt authentication mechanism

Background:
* url 'http://localhost:8080'
* def authHeader = 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJhdWQiOiJteUF1ZGllbmNlIiwiaXNzIjoibXlJc3N1ZXIiLCJyb2xlcyI6WyJhZG1pbiJdLCJleHRyYSI6eyJhIjoxLCJiIjoyfX0.ODI9RPwJOh6ao9Z9SMiHdDCvVJDzvn82MiKogjVrfPM'
* def authHeaderMissingExtraProps = 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJhdWQiOiJteUF1ZGllbmNlIiwiaXNzIjoibXlJc3N1ZXIiLCJyb2xlcyI6WyJhZG1pbiJdfQ.NIpFYSBto3R33kmLysGry_MLwWD8RgrpuPhAuBrBqT4'
* def wrongAuthHeader = 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJteUlzc3VlciIsInJvbGVzIjpbImFkbWluIl19._XdssSCrd4_qfIwQua9k4VJeHuCwl-fsk7WMg_vzFAA='
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