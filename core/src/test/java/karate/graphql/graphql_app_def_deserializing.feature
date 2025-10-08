Feature: GraphQL App correctness checker (needs Interceptor)

  Background:

    * url restheartBaseURL
    * def appDef = read('app-definitionExample.json')
    * def confDestroyer = read('delete_app_definition.feature')
    * configure charset = null



  ### TESTS ON GRAPHQL APP DESCRIPTOR ###

  Scenario: upload GraphQL app definition without descriptor (should succeed with empty descriptor)

    * remove appDef.descriptor
    * header Authorization = rhBasicAuth

    # create test-graphql db
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # create gql-apps collection
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload GraphQL app definition (descriptor is now optional)
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request appDef
    When method POST
    Then assert responseStatus == 201

    * header Authorization = rhBasicAuth

    # test if it is reachable at /graphql/<_id>
    * text query =
     """
    {
      users(limit: 2, skip:1){
        id
      }
    }
    """

    Given path '/graphql/test'
    And request {query: '#(query)'}
    When method POST
    Then status 200

    * call confDestroyer

  Scenario: upload GraphQL app definition without uri descriptor field (should use _id as uri)

    * remove appDef.descriptor.uri

    * header Authorization = rhBasicAuth

    # create test-graphql db
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # create gql-apps collection
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload GraphQL app definition
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request appDef
    When method POST
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # test if it is reachable at /graphql/<_id>
    * text query =
     """
    {
      users(limit: 2, skip:1){
        id
      }
    }
    """

    Given path '/graphql/test'
    And request {query: '#(query)'}
    When method POST
    Then status 200

    * call confDestroyer

  Scenario: upload GraphQL app definition without enable descriptor field

    * remove appDef.descriptor.enabled
    * header Authorization = rhBasicAuth

    # create test-graphql db
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # create gql-apps collection
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload GraphQL app definition
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request appDef
    When method POST
    Then assert responseStatus == 201 || responseStatus == 200

    * call confDestroyer

  Scenario: upload GraphQL app definition without description descriptor field

    * remove appDef.descriptor.description
    * header Authorization = rhBasicAuth

    # create test-graphql db
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # create gql-apps collection
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload GraphQL app definition
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request appDef
    When method POST
    Then assert responseStatus == 201 || responseStatus == 200

    * call confDestroyer


  Scenario: upload GraphQL app definition with descriptor having illegal format

    * set appDef.descriptor = 1
    * header Authorization = rhBasicAuth

    # create test-graphql db
    Given path '/test-graphql'
    And param wm = "upsert"
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # create gql-apps collection
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload GraphQL app definition
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request appDef
    When method POST
    Then assert responseStatus == 400

  Scenario: prevent URI collision - explicit uri conflicts with another app's _id

    * header Authorization = rhBasicAuth

    # create test-graphql db
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # create gql-apps collection
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload first app with _id "myapp" (will be accessible at /graphql/myapp)
    * def firstApp = read('app-definitionExample.json')
    * set firstApp._id = "myapp"
    * remove firstApp.descriptor.uri
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request firstApp
    When method POST
    Then assert responseStatus == 201

    * header Authorization = rhBasicAuth

    # try to upload second app with explicit uri "myapp" (should fail with 409 Conflict)
    * def secondApp = read('app-definitionExample.json')
    * set secondApp._id = "otherapp"
    * set secondApp.descriptor.uri = "myapp"
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request secondApp
    When method POST
    Then assert responseStatus == 409

    * header Authorization = rhBasicAuth

    # cleanup - delete myapp
    Given path '/test-graphql/gql-apps/myapp'
    And request {}
    When method DELETE
    Then status 204

  Scenario: prevent URI collision - _id conflicts with another app's explicit uri

    * header Authorization = rhBasicAuth

    # create test-graphql db
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # create gql-apps collection
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload first app with explicit uri "myapp"
    * def firstApp = read('app-definitionExample.json')
    * set firstApp._id = "first"
    * set firstApp.descriptor.uri = "myapp"
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request firstApp
    When method POST
    Then assert responseStatus == 201

    * header Authorization = rhBasicAuth

    # try to upload second app with _id "myapp" and no explicit uri (should fail with 409 Conflict)
    * def secondApp = read('app-definitionExample.json')
    * set secondApp._id = "myapp"
    * remove secondApp.descriptor.uri
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request secondApp
    When method POST
    Then assert responseStatus == 409

    * header Authorization = rhBasicAuth

    # cleanup - delete first
    Given path '/test-graphql/gql-apps/first'
    And request {}
    When method DELETE
    Then status 204



  ### TESTS ON GRAPHQL APP SCHEMA ###

  Scenario: upload GraphQL app without schema

    * remove appDef.schema
    * header Authorization = rhBasicAuth

    # create test-graphql db
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # create gql-apps collection
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload GraphQL app definition
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request appDef
    When method POST
    Then assert responseStatus == 400

  Scenario: upload GraphQL app definition with schema having illegal format

    # The schema below is illegal because there isn't a type Query
    * set appDef.schema = 'type User {id: Int! firstName: String lastName: String}'
    * header Authorization = rhBasicAuth


    # create test-graphql db
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # create gql-apps collection
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload GraphQL app definition
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request appDef
    When method POST
    Then assert responseStatus == 400


  ### TESTS ON GRAPHQL APP MAPPINGS ###


  Scenario: upload GraphQL app definition without mappings

    * remove appDef.mappings
    * header Authorization = rhBasicAuth


    # create test-graphql db
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # create gql-apps collection
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload GraphQL app definition
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request appDef
    When method POST
    Then assert responseStatus == 400


  # This scenario is needed because in a GraphQL schema Type Query is always present --> also mappings for Type Query
  # must be always present
  Scenario: upload GraphQL app definition without mappings for type Query

    * remove appDef.mappings.Query
    * header Authorization = rhBasicAuth


    # create test-graphql db
    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # create gql-apps collection
    Given path '/test-graphql/gql-apps'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload GraphQL app definition
    Given path '/test-graphql/gql-apps'
    And param wm = "upsert"
    And request appDef
    When method POST
    Then assert responseStatus == 400
