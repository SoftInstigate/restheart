Feature: Test GraphQL Aggregation Pipeline Security

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
  * def admin = basic({username: 'admin', password: 'secret'})

@ignore
Scenario: Setup GraphQL app with aggregation security test
  
  # Create test database and collection for GraphQL
  Given path '/test-gql-security'
  And header Authorization = admin
  When method PUT
  Then status 201

  Given path '/test-gql-security/products'
  And header Authorization = admin
  When method PUT
  Then status 201

  # Insert test data
  Given path '/test-gql-security/products'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  [
    {"name": "Product A", "category": "electronics", "price": 299.99},
    {"name": "Product B", "category": "electronics", "price": 399.99},
    {"name": "Product C", "category": "books", "price": 19.99}
  ]
  """
  When method POST
  Then status 200

  # Create GraphQL app with safe aggregation
  Given path '/restheart/gqlapps/test-gql-security-app'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "descriptor": {
      "name": "test-gql-security-app",
      "description": "Test GraphQL security for aggregations",
      "enabled": true,
      "uri": "test-gql-security-app"
    },
    "schema": "type Query { products: [Product] productsByCategory: [CategorySummary] unsafeProducts: [Product] } type Product { name: String category: String price: Float } type CategorySummary { _id: String totalPrice: Float count: Int }",
    "mappings": [
      {
        "dataloader": "products", 
        "type": "aggregation",
        "db": "test-gql-security",
        "collection": "products",
        "stages": [
          {"$sort": {"name": 1}}
        ]
      },
      {
        "dataloader": "productsByCategory",
        "type": "aggregation", 
        "db": "test-gql-security",
        "collection": "products",
        "stages": [
          {"$group": {"_id": "$category", "totalPrice": {"$sum": "$price"}, "count": {"$sum": 1}}},
          {"$sort": {"totalPrice": -1}}
        ]
      },
      {
        "dataloader": "unsafeProducts",
        "type": "aggregation",
        "db": "test-gql-security", 
        "collection": "products",
        "stages": [
          {"$match": {"category": "electronics"}},
          {"$out": "hacked_products"}
        ]
      }
    ]
  }
  """
  When method PUT
  Then status 201

Scenario: Safe GraphQL aggregation should work
  Given path '/graphql/test-gql-security-app'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "query": "{ products { name category price } }"
  }
  """
  When method POST
  Then status 200
  And match $.data.products == '#[3]'
  And match $.data.products[0].name == "Product A"

Scenario: Safe GraphQL aggregation with grouping should work  
  Given path '/graphql/test-gql-security-app'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "query": "{ productsByCategory { _id totalPrice count } }"
  }
  """
  When method POST
  Then status 200
  And match $.data.productsByCategory == '#[2]'
  And match $.data.productsByCategory[0]._id == "electronics"
  And match $.data.productsByCategory[0].count == 2

Scenario: Unsafe GraphQL aggregation with $out should be blocked
  Given path '/graphql/test-gql-security-app'
  And header Authorization = admin
  And header Content-Type = 'application/json'
  And request
  """
  {
    "query": "{ unsafeProducts { name category price } }"
  }
  """
  When method POST
  Then status 200
  And match $.errors == '#[1]'
  And match $.errors[0].message contains "GraphQL aggregation pipeline security violation"
  And match $.errors[0].message contains "BLACKLISTED_STAGE"
  And match $.errors[0].message contains "$out"