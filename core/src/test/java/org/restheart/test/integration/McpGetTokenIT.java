/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.bson.BsonDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.Unirest;

/**
 * {@code get_token} (#617): the short-lived JWT an agent uses to fill in the
 * {@code <token_from_get_token>} placeholder of a {@code how_to_call} descriptor.
 *
 * <p>This tool issues a credential, so the tests go past "it returned a token": they decode it,
 * check it carries the caller's own identity and no more, and — the part that actually matters —
 * send it to the REST API to prove it is accepted. A token that looks right but isn't honoured,
 * or one that is honoured but never expires, are both silent failures a shape-only assertion
 * would miss.
 */
public class McpGetTokenIT extends AbstactIT {

    private static final String BASE = "http://localhost:8080";
    private static final String ADMIN_BASIC = "Basic " + Base64.getEncoder().encodeToString("admin:secret".getBytes());

    /**
     * A <em>secured</em> endpoint, deliberately not {@code /ping}: that one is
     * {@code secure = false}, so it answers 200 to anyone and would make every assertion here
     * pass whatever the token was worth — including a forged one.
     */
    private static final String SECURED_URL = BASE + "/test-mcp-token/probe";

    private McpTestClient mcp;

    @BeforeEach
    public void setUpMcpFixture() throws Exception {
        Unirest.put(BASE + "/test-mcp-token").basicAuth("admin", "secret")
                .contentType("application/json").body("{}").asEmpty();
        Unirest.put(SECURED_URL).basicAuth("admin", "secret")
                .contentType("application/json").body("{}").asEmpty();

        mcp = new McpTestClient(BASE, ADMIN_BASIC);
        mcp.initialize();
    }

    @Test
    public void response_usesTheSameShapeAsTheTokenEndpoint() throws Exception {
        var token = mcp.callTool("get_token", "{}");

        // deliberately the field names of RESTHeart's own /token (and of OAuth): an agent that has
        // seen either recognizes this one without being told
        assertTrue(token.containsKey("access_token"), "missing access_token: " + token.toJson());
        assertEquals("Bearer", token.getString("token_type").getValue());
        assertEquals("admin", token.getString("username").getValue());
        assertTrue(token.getArray("roles").stream()
                .anyMatch(r -> "admin".equals(r.asString().getValue())), "roles must be the caller's own");
    }

    @Test
    public void token_isShortLivedAndSaysSoConsistently() throws Exception {
        var token = mcp.callTool("get_token", "{}");
        var expiresIn = token.getNumber("expires_in").longValue();

        assertTrue(expiresIn > 0 && expiresIn <= 120,
                "a token meant to be fetched right before use must not be long-lived; got " + expiresIn + "s");

        // expires_in is what an agent reads to decide whether to reuse; the JWT's own exp is what
        // the server enforces. They must agree, or the agent's arithmetic is a lie.
        var claims = claimsOf(token.getString("access_token").getValue());
        var ttlFromClaims = claims.getNumber("exp").longValue() - claims.getNumber("iat").longValue();
        assertEquals(expiresIn, ttlFromClaims,
                "expires_in and (exp - iat) disagree — iat missing or the advertised lifetime is wrong");
    }

    @Test
    public void token_carriesTheCallersIdentityAndNoMore() throws Exception {
        var claims = claimsOf(mcp.callTool("get_token", "{}").getString("access_token").getValue());

        assertEquals("admin", claims.getString("sub").getValue());
        assertTrue(claims.getArray("roles").stream()
                .anyMatch(r -> "admin".equals(r.asString().getValue())));
        // same signing policy as every other JWT this deployment issues, so the same mechanism
        // verifies it — see jwtConfigProvider in conf-overrides.yml
        assertEquals("restheart.org", claims.getString("iss").getValue());
    }

    @Test
    public void token_isActuallyAcceptedByTheRestApi() throws Exception {
        // the whole point of the feature: the agent never handles a password or an API key, and
        // what it does get has to work
        var accessToken = mcp.callTool("get_token", "{}").getString("access_token").getValue();

        var response = Unirest.get(SECURED_URL).header("Authorization", "Bearer " + accessToken).asString();

        assertEquals(200, response.getStatus(),
                "the issued token was refused by the REST API: " + response.getBody());
    }

    @Test
    public void noCredential_isRefusedByTheSameEndpoint() {
        // pins the precondition the test above relies on: without this, an endpoint that happened
        // to be unsecured would make "the token works" vacuously true
        var response = Unirest.get(SECURED_URL).asString();

        assertEquals(401, response.getStatus(), "the probe endpoint must actually require authentication");
    }

    @Test
    public void tamperedToken_isRefused() throws Exception {
        var accessToken = mcp.callTool("get_token", "{}").getString("access_token").getValue();

        // flip the last character of the signature: proves the 200 above came from a verified
        // signature and not from some unauthenticated fallback path
        var lastChar = accessToken.charAt(accessToken.length() - 1);
        var tampered = accessToken.substring(0, accessToken.length() - 1) + (lastChar == 'A' ? 'B' : 'A');

        var response = Unirest.get(SECURED_URL).header("Authorization", "Bearer " + tampered).asString();

        assertEquals(401, response.getStatus(), "a token with a broken signature must not authenticate");
    }

    @Test
    public void eachCall_issuesADistinctToken() throws Exception {
        var first = mcp.callTool("get_token", "{}").getString("access_token").getValue();
        var second = mcp.callTool("get_token", "{}").getString("access_token").getValue();

        // distinct jti, so a token can be traced to one issuance rather than to a session
        assertTrue(!claimsOf(first).getString("jti").getValue().equals(claimsOf(second).getString("jti").getValue()),
                "two calls returned the same jti");
    }

    /** The JWT payload, decoded — the middle segment is base64url-encoded JSON. */
    private static BsonDocument claimsOf(String jwt) {
        var payload = jwt.split("\\.")[1];
        return BsonDocument.parse(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    }
}
