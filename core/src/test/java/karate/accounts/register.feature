Feature: POST /auth/register

  # Tests for the user registration endpoint.
  # X-Skip-Email: true is set globally in karate-config.js.

  Background:
    * url baseUrl

  # ---------------------------------------------------------------------------
  Scenario: happy path — valid body returns 201
  # ---------------------------------------------------------------------------
    * def email = 'reg-happy-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Alice",
        "lastName":  "Smith",
        "teamName":  "Acme Corp",
        "email":     "#(email)",
        "password":  "Password123!"
      }
      """
    When method POST
    Then status 201

  # ---------------------------------------------------------------------------
  Scenario: duplicate email — second registration with same email returns 409
  # ---------------------------------------------------------------------------
    * def email = 'reg-dup-' + java.util.UUID.randomUUID() + '@example.com'
    * def body =
      """
      {
        "firstName": "Bob",
        "lastName":  "Jones",
        "teamName":  "BJ Corp",
        "email":     "#(email)",
        "password":  "Password123!"
      }
      """

    # First registration — must succeed
    Given path '/auth/register'
    And request body
    When method POST
    Then status 201

    # Second registration with the same email — must conflict
    Given path '/auth/register'
    And request body
    When method POST
    Then status 409

  # ---------------------------------------------------------------------------
  Scenario: missing password — returns 400
  # ---------------------------------------------------------------------------
    * def email = 'reg-nopwd-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Charlie",
        "lastName":  "Brown",
        "teamName":  "CB Corp",
        "email":     "#(email)"
      }
      """
    When method POST
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: missing email — returns 400
  # ---------------------------------------------------------------------------
    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Diana",
        "lastName":  "Prince",
        "teamName":  "DP Corp",
        "password":  "Password123!"
      }
      """
    When method POST
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: missing teamName — returns 400
  # ---------------------------------------------------------------------------
    * def email = 'reg-noteam-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Eve",
        "lastName":  "Adams",
        "email":     "#(email)",
        "password":  "Password123!"
      }
      """
    When method POST
    Then status 400

  # ---------------------------------------------------------------------------
  Scenario: verify DB — after registration user has status pending_verification and tenant set
  # ---------------------------------------------------------------------------
    * def email = 'reg-db-' + java.util.UUID.randomUUID() + '@example.com'

    Given path '/auth/register'
    And request
      """
      {
        "firstName": "Frank",
        "lastName":  "Castle",
        "teamName":  "FC Corp",
        "email":     "#(email)",
        "password":  "Password123!"
      }
      """
    When method POST
    Then status 201

    # Read the user document from MongoDB via the RESTHeart REST API
    Given path '/users/' + email
    And header Authorization = adminAuth
    And param rep = 's'
    When method GET
    Then status 200
    And match response.status == 'pending_verification'
    And match response.tenant == '#notnull'
    And match response.emailVerificationToken == '#notnull'
