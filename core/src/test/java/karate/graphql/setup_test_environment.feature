@ignore
Feature: Utils feature to upload GraphQL test App Definition and test data

  Background:

    * url restheartBaseURL
    * def appDef = (__arg.appDef == null) ? read('app-definitionExample.json') : __arg.appDef
    * def data1 = (__arg.data1 == null) ? read('data1.json') : __arg.data1
    * def data2 = (__arg.data2 == null) ? read('data2.json') : __arg.data2
    * configure charset = null

  Scenario: create test-graphql db

    * header Authorization = rhBasicAuth

    Given path '/test-graphql'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

  Scenario: create GraphQL App definition db and upload test configuration

    * header Authorization = rhBasicAuth

    # create test-apps collection
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

  Scenario: upload Data

    * header Authorization = rhBasicAuth

    # create users collection
    Given path '/test-graphql/users'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # creat posts collection
    Given path '/test-graphql/posts'
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = rhBasicAuth

    # upload users collection data
    Given path '/test-graphql/users'
    And request data1
    When method POST
    Then assert responseStatus == 201 || responseStatus == 200 || responseStatus == 207

    * header Authorization = rhBasicAuth

    # upload posts collection data
    Given path '/test-graphql/posts'
    And request data2
    When method POST
    Then assert responseStatus == 201 || responseStatus == 200 || responseStatus == 207






