Feature: DataLoader optimization tests

  Background:

    * url graphQLBaseURL
    * path '/testapp_opt'
    * header Authorization = rhBasicAuth
    * configure charset = null
    * def appDef = read('app-definitionExample_opt.json')
    * def data1 = read('data1.json')
    * def data2 = read('data2.json')
    * call read('setup_test_environment.feature') {appDef: #(appDef), data1: #(data1), data2: #(data2)}
    * def confDestroyer = read('delete_app_definition.feature')


  Scenario: Query containing field mapped with query optimized by DataLoader (batching enabled, caching enabled, batch size == 2)

    * text query =
    """
    {
      posts{
        authorCache{
          firstName
        }
      }
    }
    """

    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.extensions.dataloader.overall-statistics.batchLoadCount == 8
    And match response.extensions.dataloader.overall-statistics.batchInvokeCount == 4
    And match response.extensions.dataloader.overall-statistics.cacheHitCount == 8


    * call confDestroyer


  Scenario: Query containing field mapped with query optimized by DataLoader (batching enabled, caching disabled, batch size == 2)

    * text query =
    """
    {
      posts{
        authorNoCache{
          firstName
        }
      }
    }
    """

    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.extensions.dataloader.overall-statistics.batchLoadCount == 16
    And match response.extensions.dataloader.overall-statistics.batchInvokeCount == 8
    And match response.extensions.dataloader.overall-statistics.cacheHitCount == 0


    * call confDestroyer

  Scenario: Query containing field mapped with query optimized by DataLoader (batching enabled, caching disabled, batch size == 4)

    * text query =
    """
    {
      posts{
        authorBatch4{
          firstName
        }
      }
    }
    """

    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.extensions.dataloader.overall-statistics.batchLoadCount == 16
    And match response.extensions.dataloader.overall-statistics.batchInvokeCount == 4
    And match response.extensions.dataloader.overall-statistics.cacheHitCount == 0

    * call confDestroyer


  Scenario: Query containing field mapped with query optimized by DataLoader but DISABLED

    * text query =
    """
    {
      posts{
        authorNoBatch{
          firstName
        }
      }
    }
    """

    Given request {query: '#(query)'}
    When method POST
    Then status 200
    And match response.extensions.dataloader.overall-statistics.batchLoadCount == 0
    And match response.extensions.dataloader.overall-statistics.batchInvokeCount == 0
    And match response.extensions.dataloader.overall-statistics.cacheHitCount == 0


    * call confDestroyer