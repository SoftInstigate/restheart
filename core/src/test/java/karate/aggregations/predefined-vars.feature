Feature: the predefined variables of aggregations

  The paging variables (page, pagesize, limit, skip) are the page the client asks for
  (restheart#808), and the readFilter of mongoPermissions is the read filter of the permission as
  it applies to the request, interpolated and with its operators (restheart#809). Since
  restheart#727 a name starting with the at sign is resolved only by a registered resolver: before
  9.15.3 the paging variables were never bound, and the read filter arrived as stored, with the
  user variable as a literal string and its operators escaped.

Background:
    * url baseUrl
    * callonce read('predefined-vars-setup.feature')
    * def Base64 = Java.type('java.util.Base64')
    * def user = 'Basic ' + Base64.getEncoder().encodeToString('aggrvars-user:secret'.getBytes())

Scenario: @skip and @limit page the aggregation

    * header Authorization = adminAuth
    Given path '/test-aggr-vars/docs/_aggrs/paged'
    And param page = 2
    And param pagesize = 2
    And param rep = 's'
    When method GET
    Then status 200
    And match response == [{ n: 3 }, { n: 4 }]

    * header Authorization = adminAuth
    Given path '/test-aggr-vars/docs/_aggrs/paged'
    And param page = 1
    And param pagesize = 2
    And param rep = 's'
    When method GET
    Then status 200
    And match response == [{ n: 1 }, { n: 2 }]

Scenario: @mongoPermissions.readFilter applies as for a GET of the collection

    # the caller's documents, 1 and 2, and the public one, 3
    * header Authorization = user
    Given path '/test-aggr-vars/docs/_aggrs/permitted'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == [{ n: 1 }, { n: 2 }, { n: 3 }]

    # the same documents a GET of the collection returns
    * header Authorization = user
    Given path '/test-aggr-vars/docs'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[3]'

Scenario: without a readFilter the default applies, every document

    * header Authorization = adminAuth
    Given path '/test-aggr-vars/docs/_aggrs/permitted'
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[5]'
