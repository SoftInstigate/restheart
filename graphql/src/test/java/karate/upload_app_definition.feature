Feature: upload GraphQL App definition on MongoDB collection
  
  Background: 
    
    * url restheartBaseURL
    * def appDef = read('app-definitionExample.json')
    * configure charset = null


  Scenario: upload correct GraphQL App definition

    * header Authorization = rhBasicAuth
    * path 'test-apps'

    # create test-apps repository
    Given request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth
    * path 'test-apps'

    # upload GraphQL app definition
    Given request appDef
    When method POST
    Then status 200