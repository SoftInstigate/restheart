Feature: GraphQL query response test

  Background:

    * url graphQLBaseURL
    * path 'mflix'
    * configure charset = null
    * def appDef = read('app-definitionExample.json')
    * call read('upload_app_definition.feature') {appDef: #(appDef)}


  Scenario: Illegal Content-Type

    Given header Content-Type = 'text/html'
    And request {}
    When method POST
    Then status 400




  Scenario: Content-Type application/graphql
    
    * text query = 
     """
    {
      TheatersByCity(city: "New York"){
        location
      }
    }
    """

    Given header Content-Type = contTypeGraphQL
    And request query
    When method POST
    Then status 200




  Scenario: Illegal HTTP method

    Given request {}
    When method GET
    Then status 405




  Scenario: Correct HTTP method


    * text query =
    """
    {
      TheatersByCity(city: "New York"){
        location
      }
    }
    """

    Given request {query: '#(query)'}
    When method POST
    Then status 200



    
  Scenario: Undefined GraphQL query

    * text query = 
    """
    {
      UndefinedQuery(foo1: "bar1", foo2: "bar2"){
        field1
        field2
      }
    }
    """
    
    Given request {query: '#(query)'}
    When method POST
    Then status 400
    And match each response.errors[*].extensions.classification == 'ValidationError'




  Scenario: Undefined field in GraphQL query result type


      * text query = 
      """
      {
        TheatersByCity(city: "New York"){
          name
          pippo
        }
      }
      """
      
      Given request {query : '#(query)'}
      When method POST
      Then status 400
      And match each response.errors[*].extensions.classification == 'ValidationError'




  Scenario: Incorrect type argument passed to query

    * text query =
    """
    {
      TheatersByCity(city: 1){
        location
      }
    }
    """

    Given request {query : '#(query)'}
    When method POST
    Then status 400
    And match each response.errors[*].extensions.classification == 'ValidationError'



  Scenario: Undefined argument passed to query

    * text query =
    """
    {
      TheatersByCity(zipcode: "10001"){
        location
      }
    }
    """
    Given request {query : '#(query)'}
    When method POST
    Then status 400
    And match each response.errors[*].extensions.classification == 'ValidationError'



    Scenario: Query with variables

      * text query =
       """
       query TheaterOperation($city: String!)
        {
          TheatersByCity(city: $city){
            location
          }
        }
       """

      * json variables = {"city": "New York"}

      Given request {query: '#(query)', variables: #(variables)}
      When method POST
      Then status 200
