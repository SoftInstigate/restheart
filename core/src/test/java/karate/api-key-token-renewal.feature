@apikey
Feature: A token minted for an API key keeps the key's roles, through renewal

# An API key is deliberately narrower than the person holding it: mongoApiKeyAuthenticator builds
# the account with the roles named on the key document, never the user's. Renewal re-reads the
# account from the users store, so without a rule to stop it a narrow key would come back as the
# full user at the first renewal. The key here belongs to `admin`, whose own roles are
# [user, admin], and names only `keynarrow` — so a widening would be plain to see.

Background:
* url 'http://localhost:8080'
* def basic =
"""
function(creds) {
  var temp = creds.username + ':' + creds.password;
  var Base64 = Java.type('java.util.Base64');
  return 'Basic ' + Base64.getEncoder().encodeToString(temp.toString().getBytes());
}
"""
* def admin = basic({username: 'admin', password: 'secret'})

# the JWT payload is base64url; read it rather than trusting what the endpoint says about itself
* def payloadOf =
"""
function(jwt) {
  var p = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  while (p.length % 4 != 0) { p = p + '='; }
  var Base64 = Java.type('java.util.Base64');
  var String = Java.type('java.lang.String');
  return JSON.parse(new String(Base64.getDecoder().decode(p), 'UTF-8'));
}
"""

Scenario: Seed a key whose roles are narrower than its owner's

    * header Authorization = admin
    Given path 'test-apikeys'
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    * header Authorization = admin
    Given path 'test-apikeys/keys'
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    #   printf 'rhak_narrowkey' | shasum -a 256
    * header Authorization = admin
    Given path 'test-apikeys/keys/narrowkey'
    And param wm = 'upsert'
    And request { "hash": "e7631667bdbc75873fe42fa47e8ba1f1665bd97b783fd5d2ab6d348d7f8539d8", "user": "admin", "roles": ["keynarrow"] }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

Scenario: The token the key gets carries the key's roles, and says where it came from

    Given path 'token'
    And header Authorization = 'Bearer rhak_narrowkey'
    When method GET
    Then status 200
    And match response.access_token == '#present'

    * def claims = payloadOf(response.access_token)
    # the key's role, not admin's [user, admin]
    And match claims.roles == ['keynarrow']
    And match claims.sub == 'admin'
    # the marker is what makes the rule deliberate instead of accidental: without it nothing
    # distinguishes this token from one admin got with a password
    And match claims.apiKey == true
    And match claims.renewable == true

Scenario: Renewing it does not hand back the user's own roles

    Given path 'token'
    And header Authorization = 'Bearer rhak_narrowkey'
    When method GET
    Then status 200
    * def issued = response.access_token

    Given path 'token'
    And form field grant_type = 'refresh_token'
    And form field refresh_token = issued
    When method POST
    Then status 200

    * def renewed = payloadOf(response.access_token)
    # the whole point: still the key's role after a renewal that re-reads accounts for everyone else
    And match renewed.roles == ['keynarrow']
    And match renewed.apiKey == true
    # and it is still renewable, so the chain does not quietly end
    And match renewed.renewable == true

Scenario: The same user with a password gets a different token, with their own roles

    # The control, and the one that caught a real defect: the token cache was keyed on the
    # principal, the claim list and authDb — not on the roles. Two accounts exist for one name
    # here, the key's and the password's, and they were told apart only by authDb, which
    # mongoRealmAuthenticator sets and the key authenticator does not. This suite authenticates
    # with a file realm, which sets no authDb either — so the two shared one entry and whichever
    # token was minted first was served to both. In this direction that is merely wrong; in the
    # other the narrow key receives the full-privilege token its owner just got.
    Given path 'token'
    And header Authorization = admin
    When method GET
    Then status 200

    * def claims = payloadOf(response.access_token)
    And match claims.roles contains 'admin'
    And match claims.apiKey == '#notpresent'
    And match claims.renewable == true
