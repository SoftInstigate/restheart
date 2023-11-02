Feature: GraphQL query response test

  Background:

    * url graphQLBaseURL
    * path 'testapp'
    * configure charset = null
    * def appDef = read('app-definitionExample.json')
    * def data1 = read('data1.json')
    * def data2 = read('data2.json')
    * call read('setup_test_environment.feature') {appDef: #(appDef), data1: #(data1), data2: #(data2)}
    * def confDestroyer = read('delete_app_definition.feature')
    * header Authorization = rhBasicAuth


  Scenario: Illegal Content-Type

    Given header Content-Type = 'text/html'
    And request {}
    When method POST
    Then status 400

    * call confDestroyer


  Scenario: Content-Type application/graphql

    * text query =
     """
    {
      users(limit: 2, skip:1){
        id
      }
    }
    """

    Given header Content-Type = contTypeGraphQL
    And request query
    When method POST
    Then status 200

    * call confDestroyer


  Scenario: Illegal HTTP method

    Given request {}
    When method GET
    Then status 405

    * call confDestroyer


  Scenario: Correct HTTP method


    * text query =
    """
    {
      users(limit: 2, skip:1){
        id
      }
    }
    """

    Given request {query: '#(query)'}
    When method POST
    Then status 200

    * call confDestroyer


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

    * call confDestroyer



  Scenario: Undefined field in GraphQL query result type


      * text query =
       """
      {
        users(limit: 2, skip:1){
          id
          pippo
        }
      }
      """

      Given request {query : '#(query)'}
      When method POST
      Then status 400
      And match each response.errors[*].extensions.classification == 'ValidationError'

    * call confDestroyer


  Scenario: Incorrect type argument passed to query

    * text query =
     """
    {
      users(limit: "ciao", skip:1){
        id
      }
    }
    """

    Given request {query : '#(query)'}
    When method POST
    Then status 400
    And match each response.errors[*].extensions.classification == 'ValidationError'

    * call confDestroyer


  Scenario: Undefined argument passed to query

    * text query =
     """
    {
      users(limit: 2, skip:1, pippo: 1){
        id
      }
    }
    """
    Given request {query : '#(query)'}
    When method POST
    Then status 400
    And match each response.errors[*].extensions.classification == 'ValidationError'

    * call confDestroyer


  Scenario: Query with variables

    * text query =
      """
      query operation($limit: Int, $skip: Int)
      {
        users(limit: $limit, skip:$skip){
          id
        }
      }
    """

    * json variables = {"limit": 2, "skip": 1}

    Given request {query: '#(query)', variables: #(variables)}
    When method POST
    Then status 200

    * call confDestroyer


  Scenario: Query with multiple operations

    * text query =
        """
        query operation1
        {
          users(limit: 3){
            id
          }
        }
        query operation2
        {
          users(limit: 4){
            id
          }
        }
      """

    Given request {query: '#(query)', variables: {}, operationName: 'operation1'}
    When method POST
    Then status 200
    And match response.data.users == '#[3]'

    * call confDestroyer


  Scenario: Query with ascendant sort field

    * text query =
     """
  {
    users(sort: 1){
      id
    }
  }
  """

    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.data.users[0].id == 1

    * call confDestroyer


  Scenario: Query with descendant sort field

    * text query =
     """
  {
    users(sort: -1){
      id
    }
  }
  """

    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.data.users[0].id == 8

    * call confDestroyer


  Scenario: Query with limit field

    * text query =
     """
  {
    users(limit:2){
      id
    }
  }
  """

    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.data.users == '#[2]'

    * call confDestroyer


  Scenario: Query with skip field

    * text query =
      """
  {
    users(skip: 1, sort: 1){
      id
    }
  }
  """
    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.data.users[0].id == 2

    * call confDestroyer


  Scenario: Query containing field mapped with MongoDB document field having a different name

    * text query =
      """
    {
      users(limit: 1){
        firstName
      }
    }
    """

    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.data.users[0].firstName == 'name1'

    * call confDestroyer


  Scenario: Query containing field mapped with field in a nested MongoDB document

    * text query =
        """
      {
        users(limit: 1){
          phone
        }
      }
      """

    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.data.users[0].phone == '3314567888'

    * call confDestroyer


  Scenario: Query containing field mapped with a specific array element

    * text query =
        """
      {
        users(limit: 1){
          mainEmail
        }
      }
      """

    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.data.users[0].mainEmail == 'email11@example.com'

    * call confDestroyer


  Scenario: Query containing fields mapped with relations

    * text query =
        """
      {
        users(limit: 1){
          posts{
            text
            author{
              firstName
            }
          }
        }
      }
      """

    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.data.users[0].posts[0].text == 'text1'
    And match response.data.users[0].posts[0].author.firstName == 'name1'

    * call confDestroyer

    Scenario: JSON bad query request due to illegal json 1

    Given request { query: { users(limit: 1) { posts { text } } } } # this is invalid since query should be a string
    When method POST
    Then status 400

    * call confDestroyer

    Scenario: JSON bad query request due to illegal json 2

    Given request "{ query: { users }"
    When method POST
    Then status 400

    * call confDestroyer

    Scenario: JSON bad query request due to missing query field

    Given request { users(limit: 1) { posts { text } } } # this is invalid since body should be { query: "{...}" }
    When method POST
    Then status 400
    And match response.errors[0].message == 'missing query field'

    * call confDestroyer

    Scenario: JSON bad query request due to not existing operation

    Given request { query: "{ users (limit: 1) { posts { text } } }", operationName: "foo" }
    When method POST
    Then status 400
    And match response.errors[0].message == 'Unknown operation named \'foo\'.'

    * call confDestroyer

