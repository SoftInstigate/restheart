
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


    * def addressSchema = 
    """
    {
        "$schema": "https://json-schema.org/draft-04/schema#",
        "type": "object",
        "properties": {
            "address": { "type": "string" },
            "city": { "type": "string" },
            "postal-code": { "type": "string" },
            "country": { "type": "string"}
        },
        "required": ["address", "city", "country"]
    }
    """
    
    
    * def admin = basic({username: 'admin', password: 'secret'})
    * def mongo = basic({username: 'mongoPermissions', password: 'secret' })

    * def dbName = '/test-no-permissions/'
    * def setup = call read("mongodbPermissions-setup.feature") {db: '#(dbName)', coll: 'allowNone/'} 

Scenario: [NOT Allowed] Test bulk PATCH


    # test bulk patch
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowNone/*"
    And param filter = '{"_id":{"$exists":true}}'
    And request { name: "test"}
    When method PATCH
    Then status 403


    # delete database test-db
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204



Scenario: [NOT Allowed] Test bulk DELETE


    # test bulk DELETE
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowNone/*"
    And param filter = '{"_id":{"$exists":true}}'
    When method DELETE
    Then status 403


    # delete database test-mongoPermissions
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204


########################## WRITE MODE ##################################
Scenario: [NOT Allowed] Test INSERT part of write mode upsert

    # create a document if does not exist
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowNone/"
    And param wm = "upsert"
    And request {name: "Hello world"}
    When method POST
    Then status 403


    # delete database test-mongoPermissions
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204


Scenario: [NOT Allowed] Test UPDATE part of write mode upsert

    # update the previous document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowNone/"
    And param wm = "upsert"
    And request {"_id": "609d05fba44e9019e65d7x15", name: "restheart"}
    When method POST
    Then status 403


    # delete database test-mongoPermissions
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204



Scenario: [NOT Allowed] Test update write mode on existing document

    # create a document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowNone/"
    And param wm = "update"
    And request {"_id": "609d05fba44e9019e65d7x15", name: "restheart"}
    When method POST
    Then status 403


    # delete database test-mongoPermissions
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204



Scenario: [NOT Allowed] Test update write mode on missing document


    # create a document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowNone/"
    And param wm = "update"
    And request {"_id": "609d05fba44e9019e65d7x15", name: "restheart"}
    When method POST
    Then status 403

    # delete database test-db
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204



Scenario: [NOT Allowed] Test insert write mode

    # create a document
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowNone/"
    And param wm = "insert"
    And request { name: "restheart"}
    When method POST
    Then status 403


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
Scenario: [NOT Allowed] Create and delete a database

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
    Then status 403


    # delete db
    * headers { Authorization: '#(mongo)' }
    Given path dbName
    When method DELETE
    Then status 403

Scenario: [NOT Allowed] Create and delete index
    
    # create index on allowNone collection (created in setup)
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowNone/_indexes/nameIndex"
    And request {"keys": {"name": 1},"ops": {"unique": true }}
    When method PUT
    Then status 403


    # delete nameIndex
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "allowNone/_indexes/nameIndex"
    When method DELETE
    Then status 403
 

    # delete database test-db
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204


Scenario: [NOT Allowed] Create and delete a collection

    # create a collection
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "test"
    When method PUT
    Then status 403


    # delete collection
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "test"
    And headers {'If-Match' : '#(colEtag)'}
    When method DELETE
    Then status 403


    # delete database test-db
    * headers { "Authorization" : '#(admin)', 'If-Match': '#(setup.ETAG)' }
    Given path dbName
    When method DELETE
    And status 204






Scenario: [NOT Allowed] Get meta of db

    # get meta of a db
    * header Authorization = mongo
    Given path dbName + "_meta"
    When method GET
    Then status 403

Scenario: [NOT Allowed] Get meta of collection

    # get meta of a collection
    * header Authorization = mongo
    Given path dbName + "allowNone/_meta"
    When method GET
    Then status 403



Scenario: [NOT Allowed] Create, modify and delete a schema

    # create schema collection
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "_schemas"
    When method PUT
    Then status 403


    # create a schema for address
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "_schemas/address"
    And request addressSchema
    When method PUT
    Then status 403


    # get all schemas
    * headers { Authorization: '#(mongo)' }
    Given path dbName + "_schemas/"
    When method GET
    Then status 403


    # delete schema collection
    * headers { Authorization: '#(mongo)', 'If-Match': '#(etag)' }
    Given path dbName + "_schemas"
    When method DELETE
    Then status 403


    