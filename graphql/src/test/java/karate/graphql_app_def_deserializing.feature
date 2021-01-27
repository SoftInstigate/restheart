Feature: GraphQL App correctness checker (needs Interceptor)

  Background:

    * url restheartBaseURL
    * def appDef = read('app-definitionExample.json')
    * def uploader = read('upload_app_definition.feature')


  ### TESTS ON GRAPHQL APP DESCRIPTOR ###

  Scenario: upload GraphQL app definition without descriptor

    * remove appDef.descriptor
    * call uploader {appDef: #(appDef), expectedStatus: 400}


  Scenario: upload GraphQL app definition without both name and uri descriptor fields

    * remove appDef.descriptor.name
    * remove appDef.descriptor.uri
    * call uploader {appDef: #(appDef), expectedStatus: 400}

  Scenario: upload GraphQL app definition without uri descriptor field

    * remove appDef.descriptor.uri
    * call uploader {appDef: #(appDef), expectedStatus: 201}

    # TODO: try to query this GraphQL app to check if it is reachable on /graphql/<app_name>

  Scenario: upload GraphQL app definition without enable descriptor field

    * remove appDef.descriptor.enable
    * call uploader {appDef: #(appDef), expectedStatus: 201}


  Scenario: upload GraphQL app definition without description descriptor field

    * remove appDef.descriptor.description
    * call uploader {appDef: #(appDef), expectedStatus: 201}


  Scenario: upload GraphQL app definition with descriptor having illegal format

    * set appDef.descriptor = 1
    * call uploader {appDef: #(appDef), expectedStatus: 400}



  ### TESTS ON GRAPHQL APP SCHEMA ###

  Scenario: upload GraphQL app without schema

    * remove appDef.schema
    * call uploader {appDef: #(appDef), expectedStatus: 400}


  Scenario: upload GraphQL app definition with schema having illegal format

    # The schema below is illegal because there isn't a type Query
    * set appDef.schema =
    """
      type User {
        id: ObjectId!
        name: String!
        surname: String!
      }
    """

    * call uploader {appDef: #(appDef), expectedStatus: 400}

  ### TESTS ON GRAPHQL APP MAPPINGS ###


  Scenario: upload GraphQL app definition without mappings

    * remove appDef.mappings
    * call uploader {appDef: #(appDef), expectedStatus: 400}


  # This scenario is needed because in a GraphQL schema Type Query is always present --> also mappings for Type Query
  # must be always present
  Scenario: upload GraphQL app definition without mappings for type Query

    * remove appDef.mappings.Query
    * call uploader {appDef: #(appDef), expectedStatus: 400}
