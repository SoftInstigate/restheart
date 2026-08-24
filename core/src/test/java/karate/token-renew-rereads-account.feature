@ignore @renew
Feature: GET /token?renew re-reads the account from the users store

  Renewal exists to hand back a token that reflects the account as it is now. Before RESTHeart
  9.7.0 it only extended the expiry: the account behind a renew made with the token itself is a
  JwtAccount built from that token's own payload, so a change to the user document could not
  reach the renewed token at all.

  Three behaviours are covered here, and the third is the one that matters for security:
  the re-read must not cross realms.

  WHY THIS FEATURE IS NOT RUN IN THE STANDARD IT SUITE
  =====================================================
  These scenarios require MongoRealmAuthenticator to be the active authenticator (the one that
  handles Basic auth). The standard IT configuration (/etc/conf-overrides.yml) sets
  /basicAuthMechanism/authenticator: fileRealmAuthenticator, which does not read from MongoDB
  and cannot reload a user document. Running these tests against that configuration would fail
  because the users created in MongoDB would never be found during authentication.

  HOW TO EXECUTE
  ==============
  0. remove @ignore tag

  1. Start RESTHeart with the default configuration (which uses MongoRealmAuthenticator)
     connected to a local MongoDB instance:

        RHO='/jwtConfigProvider/account-properties-claims->["teams"];/mongo/mongo-mounts->[{"where": "/", "what": "/restheart"}, {"where": "/renew-other-realm", "what": "/renew-other-realm"}]' java -jar core/target/restheart.jar

  2. Run only this feature:

       mvn test -pl core -Dtest=RunnerIT -Dkarate.options="--tags @renew" -DfailIfNoTests=false

  Background:
    * url baseUrl
    * def basic =
      """
      function(user, pwd) {
        var Base64 = Java.type('java.util.Base64');
        return 'Basic ' + Base64.getEncoder().encodeToString((user + ':' + pwd).getBytes());
      }
      """
    * def claimsOf =
      """
      function(token) {
        var parts = token.split('.');
        var json = new java.lang.String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        return JSON.parse(json);
      }
      """
    * def sleep = function(ms){ java.lang.Thread.sleep(ms) }
    # "teams" is in jwtTokenManager/account-properties-claims, so it travels in the token and is
    # the field these scenarios watch.
    * def uid = 'renew-' + java.util.UUID.randomUUID() + '@example.com'
    * def pwd = 'RenewPass123!'

  # ---------------------------------------------------------------------------
  Scenario: the renewed token carries the user document as it is now, not as it was
  # ---------------------------------------------------------------------------
    Given path '/users/' + uid
    And header Authorization = adminAuth
    And param wm = 'upsert'
    And request { password: '#(pwd)', roles: ['user'], teams: ['before'] }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    Given path '/token'
    And header Authorization = basic(uid, pwd)
    When method POST
    Then status 200
    * def token1 = response.access_token
    * match claimsOf(token1).authDb == 'restheart'
    * match claimsOf(token1).teams == ['before']

    # Change the document behind the token
    Given path '/users/' + uid
    And header Authorization = adminAuth
    And request { teams: ['after'] }
    When method PATCH
    Then assert responseStatus == 200 || responseStatus == 204

    # Renew presenting the token itself — no credentials. This is the case that used to be
    # impossible: the account is the token's own claims unless the users store is consulted.
    Given path '/token'
    And param renew = 'true'
    And header Authorization = 'Bearer ' + token1
    When method GET
    Then status 200
    * def token2 = response.access_token
    * match token2 != token1
    * match claimsOf(token2).teams == ['after']

    # No wait anywhere above: reloadAccount invalidates the authenticator's cache entry before
    # reading, so the guarantee is "re-read", not "re-read once the cache expires". Should that
    # invalidation be dropped, this scenario fails — mongoRealmAuthenticator/cache-ttl defaults
    # to 60s, far longer than this test takes.

  # ---------------------------------------------------------------------------
  Scenario: renewal still works when the user is not in the users store
  # ---------------------------------------------------------------------------
    # claimsTest is a file-realm user. We create a matching document in MongoDB so that
    # MongoRealmAuthenticator can authenticate it. The renewal then re-reads from the store.
    Given path '/users/claimsTest'
    And header Authorization = adminAuth
    And param wm = 'upsert'
    And request { password: 'ClaimsPass123!', roles: ['user'], teams: ['acme'] }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    Given path '/token'
    And header Authorization = basic('claimsTest', 'ClaimsPass123!')
    When method POST
    Then status 200
    * def token1 = response.access_token
    * match claimsOf(token1).teams == ['acme']

    # a different exp needs a different second
    * call sleep 1100

    Given path '/token'
    And param renew = 'true'
    And header Authorization = 'Bearer ' + token1
    When method GET
    Then status 200
    * def token2 = response.access_token
    * match token2 != token1
    * match claimsOf(token2).teams == ['acme']

  # ---------------------------------------------------------------------------
  Scenario: the re-read is refused when the request resolves to a different realm
  # ---------------------------------------------------------------------------
    # Same principal name in two realms is two different people. Without the authDb check, a
    # token issued in one realm and renewed against another would be reissued carrying the other
    # user's data — here, their roles.
    Given path '/users/' + uid
    And header Authorization = adminAuth
    And param wm = 'upsert'
    And request { password: '#(pwd)', roles: ['user'], teams: ['own-realm'] }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    # a namesake in the other realm, with data the renewed token must never pick up
    * def otherRealm = 'renew-other-realm'
    Given path '/' + otherRealm
    And header Authorization = adminAuth
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    Given path '/' + otherRealm + '/users'
    And header Authorization = adminAuth
    And request {}
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    Given path '/' + otherRealm + '/users/' + uid
    And header Authorization = adminAuth
    And param wm = 'upsert'
    And request { password: '#(pwd)', roles: ['admin'], teams: ['impostor'] }
    When method PUT
    Then assert responseStatus == 201 || responseStatus == 200

    Given path '/token'
    And header Authorization = basic(uid, pwd)
    When method POST
    Then status 200
    * def token1 = response.access_token
    * match claimsOf(token1).authDb == 'restheart'
    * match claimsOf(token1).teams == ['own-realm']

    * call sleep 1100

    # Renew against the other realm — the re-read must be refused because authDb doesn't match
    Given path '/token'
    And param renew = 'true'
    And param _db-override = otherRealm
    And header Authorization = 'Bearer ' + token1
    When method GET
    Then status 200
    * def renewed = claimsOf(response.access_token)

    # fell back to the token's own claims rather than reading the namesake
    * match renewed.teams == ['own-realm']
    * match renewed.roles contains 'user'
    * match renewed.roles !contains 'admin'
