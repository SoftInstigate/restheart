Feature: Utils feature to upload App Definition om MongoDB
  
  Background: 
    
    * url restheartBaseURL
    * def appDef = (__arg.appDef == null) ? read('app-definitionExample.json') : __arg.appDef
    * def expectedStatus = (__arg.expectedStatus == null) ? 201 : __arg.expectedStatus
    * configure charset = null


  Scenario: upload GraphQL App definition

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
    Then match responseStatus == expectedStatus

