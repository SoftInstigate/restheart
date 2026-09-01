Feature: test apiKeyAuthMechanism

# What the unit tests cannot reach.
#
# ApiKeyAuthMechanismTest exercises the mechanism against a stub authenticator,
# in isolation. The claim the whole design rests on is a different one: that this
# mechanism and jwtAuthenticationMechanism share the Bearer scheme in a running
# instance, in the right order. That order comes from @RegisterPlugin(priority)
# via PluginsFactory -> PluginsRegistryImpl -> AuthenticatorMechanismsHandler,
# and was established by reading the code rather than by observing it.
#
# If that reasoning were wrong, every unit test would still pass and the feature
# would be broken — the JWT mechanism would answer NOT_AUTHENTICATED for a key
# and end the chain. Hence the first two scenarios below, which are the point of
# this file; the rest is behaviour that is cheaper to pin here than to mock.

Background:
  * callonce read('api-key-auth-setup.feature')
  * url 'http://localhost:8080'

  # roles: [admin], alg=HS256, key=secret — the same token jwt-auth.feature uses
  * def jwt = 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJyZXN0aGVhcnQub3JnIiwicm9sZXMiOlsiYWRtaW4iXSwiZXh0cmEiOnsiYSI6MSwiYiI6Mn19.vDaJOoPH5EnAiyM6lF737vqgi978S2GIAQe1gq33eDU'

Scenario: a JWT still authenticates with apiKeyAuthMechanism enabled ahead of it
    # The regression that matters. Enabling a mechanism that also reads Bearer
    # must not change the outcome of a request that carries a JWT.
    * header Authorization = jwt
    Given path '/secho'
    When method GET
    Then status 200

Scenario: an API key authenticates under the same Bearer scheme
    * header Authorization = 'Bearer rhak_valid'
    Given path '/secho'
    When method GET
    Then status 200

Scenario: the specific roles on the key drive authorization
    # The test ACL grants poweruser GET /testdb and nothing else, so this key
    # reaching one and not the other shows the roles came from the key document
    # rather than from the user it belongs to — that user is `admin`, who would
    # have reached both.
    * header Authorization = 'Bearer rhak_poweruser'
    Given path '/testdb'
    When method GET
    Then assert responseStatus == 200 || responseStatus == 404

    * header Authorization = 'Bearer rhak_poweruser'
    Given path '/secho'
    When method GET
    Then status 403

Scenario: a key naming no roles authenticates but reaches nothing
    # Deny-by-default, end to end: authentication succeeds — so this is 403 and
    # not 401 — and then the ACL matches nothing.
    * header Authorization = 'Bearer rhak_noroles'
    Given path '/secho'
    When method GET
    Then status 403

Scenario: an expired key is refused even though it is still in the collection
    * header Authorization = 'Bearer rhak_expired'
    Given path '/secho'
    When method GET
    Then status 401

Scenario: an unknown key with the prefix is refused rather than passed on
    * header Authorization = 'Bearer rhak_nosuchkey'
    Given path '/secho'
    When method GET
    Then status 401

Scenario: a Bearer value with no prefix is left to the JWT mechanism
    # Not ours, so we decline; the JWT mechanism owns the answer and the answer
    # is 401. What must not happen is this reaching the key lookup.
    * header Authorization = 'Bearer not-a-token'
    Given path '/secho'
    When method GET
    Then status 401

Scenario: no Authorization header at all
    Given path '/secho'
    When method GET
    Then status 401
