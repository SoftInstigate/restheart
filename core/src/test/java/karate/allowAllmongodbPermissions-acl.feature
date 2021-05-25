
Feature: Test mongodb permissions in acl file
  
Background:
    * url 'http://localhost:8080'
    * def basic =
    """
        function(creds) {
            var temp = creds.username + ':' + creds.password;
            var Base64 = Java.type('java.util.Base64');
            var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
            return 'Basic ' + encoded;
        }
    """


    * def addressSchema = { _id: "address", "$schema": "https://json-schema.org/draft-04/schema#","type": "object","properties": { "address": { "type": "string" },"city": { "type": "string" },"postal-code": { "type": "string" },"country": { "type": "string"}},"required": ["address", "city", "country"]}
    
    
    * def admin = basic({username: 'admin', password: 'secret'})
    * def mongo = basic({username: 'mongoPermissions', password: 'secret' })

    * def setup = call read("mongodbPermissions-setup.feature") {db: '/test-all-permissions/', coll: 'allowAll/'} 
      
    * def dbName = '/test-all-permissions/'

Scenario: [Allowed] Test bulk PATCH

    # create a document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And request {name: "Hello World"}
    When method POST
    Then status 201


    # test bulk patch
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/*"
    And param filter = '{"_id":{"$exists":true}}'
    And request { name: "test"}
    When method PATCH
    Then status 200


    # check results
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param rep = 's'
    When method GET
    Then status 200
    And match each response contains {name: "test"}


    # delete database test-mongoPermissions
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204



Scenario: [Allowed] Test bulk DELETE

    # create a document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And request {name: "Hello world"}
    When method POST
    Then status 201


    # test bulk DELETE
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/*"
    And param filter = '{"_id":{"$exists":true}}'
    When method DELETE
    Then status 200


    # check results
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[0]'


    # delete database test-mongoPermissions
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204


########################## WRITE MODE ##################################
Scenario: [Allowed] Test INSERT part of write mode upsert

    # create a document if does not exist
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param wm = "upsert"
    And request {name: "Hello world"}
    When method POST
    Then status 201


    # check results
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'


    # delete database test-mongoPermissions
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204


Scenario: [Allowed] Test UPDATE part of write mode upsert

    # create a document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And request { _id: "609d05fba44e9019e65d7x15", name: "Hello world"}
    When method POST
    Then status 201


    # update the previous document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param wm = "upsert"
    And request {"_id": "609d05fba44e9019e65d7x15", name: "restheart"}
    When method POST
    Then status 200


    # check results
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'


    # delete database test-mongoPermissions
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204



Scenario: [Allowed] Test update write mode on existing document

    # create a document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And request { _id: "609d05fba44e9019e65d7x15", name: "Hello world"}
    When method POST
    Then status 201


    # create a document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param wm = "update"
    And request {"_id": "609d05fba44e9019e65d7x15", name: "restheart"}
    When method POST
    Then status 200


    # check results
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'


    # delete database test-mongoPermissions
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204



Scenario: [Allowed] Test update write mode on missing document


    # create a document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param wm = "update"
    And request {"_id": "609d05fba44e9019e65d7x15", name: "restheart"}
    When method POST
    Then status 404


    # check results
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[0]'


    # delete database test-db
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204



Scenario: [Allowed] Test insert write mode

    # create a document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param wm = "insert"
    And request { name: "restheart"}
    When method POST
    Then status 201


    # check results
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/"
    And param rep = 's'
    When method GET
    Then status 200
    And match response == '#[1]'


    # delete database test-db
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204

########################## END WRITE MODE ##################################

########################## MANAGEMENT REQUESTS ##################################
# indexes
# dbs
# collections
# schemas
# meta
Scenario: [Allowed] Create and delete a database

    # if database exists delete it with admin permissions
    * headers { Authorization: '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    And param filter = '{"_id":{"$exists":true}}'
    When method DELETE
    Then status 204

    # create db without admin permissions
    * headers { Authorization: '#(mongo)' }
    Given path dbName
    When method PUT
    Then status 201
    * def ETAG = responseHeaders['ETag'][0]


    # delete db
    * headers { Authorization: '#(mongo)' }
    Given path dbName
    And headers { 'If-Match' : '#(ETAG)'}
    When method DELETE
    Then status 204

Scenario: [Allowed] Create and delete index
    
    # create index on allowAll collection (created in setup)
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/_indexes/nameIndex"
    And request {"keys": {"name": 1},"ops": {"unique": true }}
    When method PUT
    Then status 201


    # delete nameIndex
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowAll/_indexes/nameIndex"
    When method DELETE
    Then status 204
 

    # delete database test-db
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204


Scenario: [Allowed] Create and delete a collection

    # create a collection
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "test"
    When method PUT
    Then status 201
    * def colEtag = responseHeaders['ETag'][0]


    # delete collection
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "test"
    And headers {'If-Match' : '#(colEtag)'}
    When method DELETE
    Then status 204


    # delete database test-db
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204






Scenario: [Allowed] Get meta of db

    # get meta of a db
    * header Authorization = mongo
    Given path dbName + "_meta"
    When method GET
    Then status 200

Scenario: [Allowed] Get meta of collection

    # get meta of a collection
    * header Authorization = mongo
    Given path dbName + "allowAll/_meta"
    When method GET
    Then status 200



Scenario: [Allowed] Create, modify and delete a schema

    # create schema collection
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "_schemas"
    When method PUT
    Then status 201
    * def etag = responseHeaders['ETag'][0]


    # create a schema for address
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "_schemas/"
    And request addressSchema
    When method POST
    Then status 201


    # get all schemas
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "_schemas/"
    When method GET
    Then status 200


    # delete schema collection
    * headers { Authorization: '#(mongo)', 'If-Match': '#(etag)' }
    Given path dbName + "_schemas"
    When method DELETE
    Then status 204


    