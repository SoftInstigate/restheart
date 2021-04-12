Feature: GraphQL App correctness checker (needs Interceptor)

  Background:

    * url restheartBaseURL
    * def appDef = read('app-definitionExample.json')
    * def confDestroyer = read('delete_app_definition.feature')
    * configure charset = null



  ### TESTS ON GRAPHQL APP DESCRIPTOR ###

  Scenario: upload GraphQL app definition without descriptor

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

    # upload GraphQL app definition
    Given path '/test-graphql/gql-apps'
    And request appDef
    When method POST
    Then assert responseStatus == 400

  Scenario: upload GraphQL app definition without both name and uri descriptor fields

    * remove appDef.descriptor.name
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
    And request appDef
    When method POST
    Then assert responseStatus == 400

  Scenario: upload GraphQL app definition without uri descriptor field

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
    And request appDef
    When method POST
    Then assert responseStatus == 201

    * header Authorization = rhBasicAuth

    # test if it is reachable at /graphql/<app-name>
    * text query =
     """
    {
      users(limit: 2, skip:1){
        id
      }
    }
    """

    Given path '/graphql/test-app'
    And request {query: '#(query)'}
    When method POST
    Then status 200

    * call confDestroyer

  Scenario: upload GraphQL app definition without enable descriptor field

    * remove appDef.descriptor.enable
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
    And request appDef
    When method POST
    Then assert responseStatus == 201

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
    And request appDef
    When method POST
    Then assert responseStatus == 201

    * call confDestroyer


  Scenario: upload GraphQL app definition with descriptor having illegal format

    * set appDef.descriptor = 1
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
    And request appDef
    When method POST
    Then assert responseStatus == 400



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
    And request appDef
    When method POST
    Then assert responseStatus == 400
