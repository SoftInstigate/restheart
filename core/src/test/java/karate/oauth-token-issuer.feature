Feature: the Authorization Code flow issues what a registered OAuthTokenIssuer decides (#740)

  # testOAuthTokenIssuer (test-plugins) acts only on requests carrying ?ti: it offers a role and
  # a number of days, seals the choice in the code, and mints test-token:<role>:<days> at /token.
  # Without ti it stays out, which is what keeps oauth-authorization-code.feature true.

Background:
* url 'http://localhost:8080'
* configure followRedirects = false
* def codeVerifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'
* def computeCodeChallenge =
"""
function(verifier) {
  var MessageDigest = Java.type('java.security.MessageDigest');
  var Base64 = Java.type('java.util.Base64');
  var digest = MessageDigest.getInstance('SHA-256');
  var hashBytes = digest.digest(verifier.getBytes('US-ASCII'));
  return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
}
"""
* def codeChallenge = computeCodeChallenge(codeVerifier)
* def extractQueryParam =
"""
function(url, param) {
  var idx = url.indexOf(param + '=');
  if (idx < 0) return null;
  var start = idx + param.length + 1;
  var end = url.indexOf('&', start);
  var value = end < 0 ? url.substring(start) : url.substring(start, end);
  return decodeURIComponent(value);
}
"""
* def basic =
"""
function(creds) {
  var temp = creds.username + ':' + creds.password;
  var Base64 = Java.type('java.util.Base64');
  var encoded = Base64.getEncoder().encodeToString(temp.toString().getBytes());
  return 'Basic ' + encoded;
}
"""
* def adminBasic = basic({username: 'admin', password: 'secret'})

# ------------------------------------------------------------------ GET /authorize/offer

Scenario: /authorize/offer without credentials is 401, and No-Auth-Challenge keeps the browser's own dialog away
    Given path '/authorize/offer'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param ti = '1'
    And header No-Auth-Challenge = 'true'
    When method GET
    Then status 401
    And match responseHeaders['WWW-Authenticate'] == '#notpresent'

Scenario: /authorize/offer with wrong credentials is 401: it is the page's password check
    Given path '/authorize/offer'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param ti = '1'
    And header Authorization = basic({username: 'admin', password: 'wrong'})
    And header No-Auth-Challenge = 'true'
    When method GET
    Then status 401

Scenario: nothing to choose is 204, so the page submits at once
    # no ti: the issuer stays out, exactly as if none were registered
    Given path '/authorize/offer'
    And param redirect_uri = 'http://localhost:3000/callback'
    And header Authorization = adminBasic
    When method GET
    Then status 204

Scenario: an offer is 200 with the choices the page renders
    Given path '/authorize/offer'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param ti = '1'
    And header Authorization = adminBasic
    When method GET
    Then status 200
    And match responseHeaders['Cache-Control'][0] == 'no-store'
    And match response.title == 'Test issuer'
    And match response.message contains 'admin'
    And match response.choices == '#[2]'
    And match response.choices[0] == { name: 'role', label: 'Role', type: 'select', options: [{ value: 'reader', label: 'reader' }, { value: 'writer', label: 'writer' }], value: 'reader' }
    And match response.choices[1] == { name: 'days', label: 'Valid for (days)', type: 'number', min: 1, max: 365, value: '30' }

Scenario: a refusal is 403 with the reason, and the page stops there
    Given path '/authorize/offer'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param ti = 'refuse'
    And header Authorization = adminBasic
    When method GET
    Then status 403
    And match response.error == 'access_denied'
    And match response.error_description == 'this account may not obtain a token here'

# ------------------------------------------------------------------ the choice, sealed and minted

Scenario: the choice sealed at /authorize is what /token mints, and it does not refresh
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param state = 'issuer-state'
    And param ti = '1'
    And param choice.role = 'writer'
    And param choice.days = '7'
    And header Authorization = adminBasic
    When method POST
    Then status 302
    And def location = responseHeaders['Location'][0]
    And match location contains 'http://localhost:3000/callback'
    And match location contains 'state=issuer-state'
    And def authCode = extractQueryParam(location, 'code')

    Given path '/token'
    And form field grant_type = 'authorization_code'
    And form field code = authCode
    And form field redirect_uri = 'http://localhost:3000/callback'
    And form field client_id = 'test-client'
    And form field code_verifier = codeVerifier
    When method POST
    Then status 200
    And match response.access_token == 'test-token:writer:7'
    And match response.token_type == 'Bearer'
    And match response.expires_in == 604800
    And match response.refresh_token == '#notpresent'
    And match response.username == '#notpresent'
    And match response.roles == '#notpresent'
    And match responseHeaders['Cache-Control'][0] == 'no-store'

    # what the issuer minted is not a JWT, so the refresh grant has nothing to renew: the
    # narrow credential cannot come back as the wide one
    Given path '/token'
    And form field grant_type = 'refresh_token'
    And form field refresh_token = response.access_token
    When method POST
    Then status 400
    And match response.error == 'invalid_grant'

Scenario: scope preselects a choice, and what the page sends overrides it
    # the client asked for writer for 3 days; the page sent nothing for role and 5 for days
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param scope = 'role:writer days:3'
    And param ti = '1'
    And param choice.days = '5'
    And header Authorization = adminBasic
    When method POST
    Then status 302
    And def authCode = extractQueryParam(responseHeaders['Location'][0], 'code')

    Given path '/token'
    And form field grant_type = 'authorization_code'
    And form field code = authCode
    And form field redirect_uri = 'http://localhost:3000/callback'
    And form field client_id = 'test-client'
    And form field code_verifier = codeVerifier
    When method POST
    Then status 200
    And match response.access_token == 'test-token:writer:5'

Scenario: the issuer's defaults apply when neither scope nor the page chose
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param ti = '1'
    And header Authorization = adminBasic
    When method POST
    Then status 302
    And def authCode = extractQueryParam(responseHeaders['Location'][0], 'code')

    Given path '/token'
    And form field grant_type = 'authorization_code'
    And form field code = authCode
    And form field redirect_uri = 'http://localhost:3000/callback'
    And form field client_id = 'test-client'
    And form field code_verifier = codeVerifier
    When method POST
    Then status 200
    And match response.access_token == 'test-token:reader:30'

Scenario: without ti the same flow issues the JWT, as it always has
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param choice.role = 'writer'
    And header Authorization = adminBasic
    When method POST
    Then status 302
    And def authCode = extractQueryParam(responseHeaders['Location'][0], 'code')

    Given path '/token'
    And form field grant_type = 'authorization_code'
    And form field code = authCode
    And form field redirect_uri = 'http://localhost:3000/callback'
    And form field client_id = 'test-client'
    And form field code_verifier = codeVerifier
    When method POST
    Then status 200
    And match response.access_token != 'test-token:writer:30'
    And match response.access_token == '#regex ^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$'
    And match response.refresh_token == '#present'

# ------------------------------------------------------------------ refusals, before a code exists

Scenario: a choice outside the offer is refused, back to the sign-in page when the credentials came from its form
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param state = 'refused-state'
    And param ti = '1'
    And param choice.role = 'root'
    And header Content-Type = 'application/x-www-form-urlencoded'
    And request 'username=admin&password=secret'
    When method POST
    Then status 302
    And def location = responseHeaders['Location'][0]
    And match location contains 'http://localhost:3000/login'
    And match location contains 'error=invalid_scope'
    And match location contains 'error_description='
    And match location !contains 'code='
    # the OAuth context survives the round trip, so the page can offer again
    And match location contains 'state=refused-state'
    And match location contains 'code_challenge='

Scenario: the same refusal goes to the client as an OAuth error when there is no page in the loop
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param state = 'refused-state'
    And param scope = 'role:root'
    And param ti = '1'
    And header Authorization = adminBasic
    When method POST
    Then status 302
    And def location = responseHeaders['Location'][0]
    And match location contains 'http://localhost:3000/callback'
    And match location contains 'error=invalid_scope'
    And match location contains 'state=refused-state'
    And match location !contains 'code='

Scenario: an account the issuer refuses gets no code at all
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param ti = 'refuse'
    And header Authorization = adminBasic
    When method POST
    Then status 302
    And def location = responseHeaders['Location'][0]
    And match location contains 'error=access_denied'
    And match location !contains 'code='

Scenario: days out of bounds is refused with the issuer's own words
    Given path '/authorize'
    And param response_type = 'code'
    And param client_id = 'test-client'
    And param redirect_uri = 'http://localhost:3000/callback'
    And param code_challenge = codeChallenge
    And param code_challenge_method = 'S256'
    And param ti = '1'
    And param choice.days = '9999'
    And header Authorization = adminBasic
    When method POST
    Then status 302
    And def location = responseHeaders['Location'][0]
    And match location contains 'error=invalid_request'
    And match location contains 'error_description=days+must+be+between+1+and+365'
    And match location !contains 'code='
