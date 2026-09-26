@ignore
Feature: helper — uploads a text file to be chunked in the background, one file of the database at a time

Scenario:
    * url 'http://localhost:8080'
    * header Authorization = 'Basic YWRtaW46c2VjcmV0'
    Given path db + '/notes.files'
    And param _ai-chunking-async = 'true'
    And param _ai-chunking-max-concurrent = '1'
    And multipart file file = { value: '#(text)', filename: '#(name)', contentType: 'text/plain' }
    And multipart field metadata = '{ "filename": "' + name + '" }'
    When method POST
    Then status 201
    * def fileId = responseHeaders['Location'][0].substring(responseHeaders['Location'][0].length - 24)
