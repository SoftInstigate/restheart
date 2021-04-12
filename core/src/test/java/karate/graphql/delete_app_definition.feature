@ignore
Feature: Utils feature to delete test App Definition on MongoDB

  Background:

    * url restheartBaseURL
    * configure charset = null

  Scenario: delete GraphQL test app configuration

    * header Authorization = rhBasicAuth

    Given path '/test-graphql/gql-apps/test'
    And request {}
    When method DELETE
    Then status 204

