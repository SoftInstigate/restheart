@ignore
Feature: helper — the documents of a collection matching a filter, in chunk order

Scenario:
    * url 'http://localhost:8080'
    * header Authorization = 'Basic YWRtaW46c2VjcmV0'
    Given path '/ai-test-chunk-rules/' + coll
    And param filter = filter
    And param sort = '{"chunkIndex": 1}'
    And param rep = 's'
    When method GET
    # a collection nothing was ever written to answers 404: no chunks either way
    * def chunks = responseStatus == 404 ? [] : response
