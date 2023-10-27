Feature: Test rootDoc in query and aggregation mappings for GraphQL plugin

Background:
    * url graphQLBaseURL
    * path 'test-rootDoc'

    * def basic =
    """
    function(creds) {
    var temp = creds.username + ':' + creds.password;
    var Base64 = Java.type('java.util.Base64');
    var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
    return 'Basic ' + encoded;
    }
    """

    * def admin = basic({username: 'admin', password: 'secret'})

    * def setupData = callonce read('setup.feature')

Scenario: Should return three authors with visible posts and group description

    * text query =
    """
   {
     authors{
       _id
       properties {
        group {
            description
        }
        genre
       }
       posts(visible: true) {
        content
       }
     }
   }
   """

   Given header Content-Type = contTypeGraphQL
   And header Authorization = admin
   And request query
   When method POST
   Then status 200

   And match $.data.authors.length() == 3

   And match $.data.authors[0]._id == 'bar'
   And match $.data.authors[0].posts.length() == 1
   And match $.data.authors[0].properties.group.description == 'the coolest authors'
   And match $.data.authors[0].properties.genre == 'crime'

   And match $.data.authors[1]._id == 'foo'
   And match $.data.authors[1].posts.length() == 2
   And match $.data.authors[1].properties.group.description == 'the coolest authors'
   And match $.data.authors[1].properties.genre == 'sci-fi'

   And match $.data.authors[2]._id == 'zum'
   And match $.data.authors[2].posts.length() == 1
   And match $.data.authors[2].properties.group.description == 'authors somehow wierd'
   And match $.data.authors[2].properties.genre == 'action'


Scenario: Should return three authors with invisible posts and group description

    * text query =
    """
   {
     authors{
       _id
       properties {
        group {
            description
        }
        genre
       }
       posts(visible: false) {
        content
       }
     }
   }
   """

   Given header Content-Type = contTypeGraphQL
   And header Authorization = admin
   And request query
   When method POST
   Then status 200

   And match $.data.authors.length() == 3

   And match $.data.authors[0]._id == 'bar'
   And match $.data.authors[0].posts.length() == 2
   And match $.data.authors[0].properties.group.description == 'the coolest authors'
   And match $.data.authors[0].properties.genre == 'crime'

   And match $.data.authors[1]._id == 'foo'
   And match $.data.authors[1].posts.length() == 1
   And match $.data.authors[1].properties.group.description == 'the coolest authors'
   And match $.data.authors[1].properties.genre == 'sci-fi'

   And match $.data.authors[2]._id == 'zum'
   And match $.data.authors[2].posts.length() == 2
   And match $.data.authors[2].properties.group.description == 'authors somehow wierd'
   And match $.data.authors[2].properties.genre == 'action'

Scenario: rootDoc should work when a type without mappings is involved

    * text query =
    """
    {
     authors{
       _id
       properties {
        group {
            description
        }
        genre
       }
       propertiesWrapperNoMapping {
        properties {
            group {
                description
            }
            genre
        }
       }
     }
   }
   """

   Given header Content-Type = contTypeGraphQL
   And header Authorization = admin
   And request query
   When method POST
   Then status 200

   And match $.data.authors.length() == 3

   And match $.data.authors[0]._id == 'bar'
   And match $.data.authors[0].properties.group.description == $.data.authors[0].propertiesWrapperNoMapping.properties.group.description
   And match $.data.authors[0].properties.genre == $.data.authors[0].propertiesWrapperNoMapping.properties.genre

   And match $.data.authors[1]._id == 'foo'
   And match $.data.authors[1].properties.group.description == $.data.authors[1].propertiesWrapperNoMapping.properties.group.description
   And match $.data.authors[1].properties.genre == $.data.authors[1].propertiesWrapperNoMapping.properties.genre

   And match $.data.authors[2]._id == 'zum'
   And match $.data.authors[2].properties.group.description == $.data.authors[2].propertiesWrapperNoMapping.properties.group.description
   And match $.data.authors[2].properties.genre == $.data.authors[2].propertiesWrapperNoMapping.properties.genre

Scenario: rootDoc should work when a type with partial mappings is involved

    * text query =
    """
    {
     authors{
       _id
       properties {
        group {
            description
        }
        genre
       }
       propertiesWrapperPartialMapping {
        properties {
            group {
                description
            }
            genre
        }
        version
       }
     }
   }
   """

   Given header Content-Type = contTypeGraphQL
   And header Authorization = admin
   And request query
   When method POST
   Then status 200

   And match $.data.authors.length() == 3

   And match $.data.authors[0]._id == 'bar'
   And match $.data.authors[0].propertiesWrapperPartialMapping.version == 1
   And match $.data.authors[0].properties.group.description == $.data.authors[0].propertiesWrapperPartialMapping.properties.group.description
   And match $.data.authors[0].properties.genre == $.data.authors[0].propertiesWrapperPartialMapping.properties.genre

   And match $.data.authors[1]._id == 'foo'
   And match $.data.authors[1].propertiesWrapperPartialMapping.version == 2
   And match $.data.authors[1].properties.group.description == $.data.authors[1].propertiesWrapperPartialMapping.properties.group.description
   And match $.data.authors[1].properties.genre == $.data.authors[1].propertiesWrapperPartialMapping.properties.genre

   And match $.data.authors[2]._id == 'zum'
   And match $.data.authors[2].propertiesWrapperPartialMapping.version == 1
   And match $.data.authors[2].properties.group.description == $.data.authors[2].propertiesWrapperPartialMapping.properties.group.description
   And match $.data.authors[2].properties.genre == $.data.authors[2].propertiesWrapperPartialMapping.properties.genre

