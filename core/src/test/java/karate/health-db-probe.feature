Feature: /health/db probe endpoint

Scenario: GET /health/db returns ok when MongoDB is up
* url 'http://localhost:8080/health/db'
When method get
Then status 200
* match response.status == 'ok'
* match response.db == 'admin'
* assert response.pingMs >= 0
