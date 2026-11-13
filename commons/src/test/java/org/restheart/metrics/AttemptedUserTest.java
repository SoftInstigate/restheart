/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * The user a request tries to authenticate as, read from the Authorization header: what the
 * brute force guard counts failures on, per source.
 */
public class AttemptedUserTest {
    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String jwt(String payloadJson) {
        var enc = Base64.getUrlEncoder().withoutPadding();
        return "Bearer " + enc.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8))
                + "." + enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8))
                + "." + enc.encodeToString("sig".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void basicUserid() {
        assertEquals("alice", Metrics.attemptedUser(basic("alice", "wrong")).orElseThrow());
    }

    @Test
    public void basicUseridWithColonInPassword() {
        assertEquals("alice", Metrics.attemptedUser(basic("alice", "pa:ss")).orElseThrow());
    }

    @Test
    public void basicNotBase64IsNoUser() {
        assertTrue(Metrics.attemptedUser("Basic ***").isEmpty());
    }

    @Test
    public void digestUsername() {
        assertEquals("bob", Metrics.attemptedUser("Digest username=\"bob\", realm=\"RESTHeart\", nonce=\"x\"").orElseThrow());
    }

    @Test
    public void jwtSubject() {
        assertEquals("carol", Metrics.attemptedUser(jwt("{\"sub\":\"carol\",\"roles\":[\"user\"]}")).orElseThrow());
    }

    @Test
    public void jwtSubjectWithSpacesAroundTheColon() {
        assertEquals("carol", Metrics.attemptedUser(jwt("{ \"sub\" : \"carol\" }")).orElseThrow());
    }

    @Test
    public void jwtWithoutSubjectIsFingerprinted() {
        var user = Metrics.attemptedUser(jwt("{\"roles\":[\"user\"]}")).orElseThrow();
        assertTrue(user.startsWith("token:"), user);
    }

    @Test
    public void apiKeyIsFingerprintedNotExposed() {
        var key = "rhak_" + "a".repeat(43);
        var user = Metrics.attemptedUser("Bearer " + key).orElseThrow();
        assertTrue(user.startsWith("token:"), user);
        assertEquals("token:".length() + 16, user.length());
        assertTrue(!user.contains("rhak_"), "the key itself never becomes a metric name");
    }

    @Test
    public void twoKeysTwoUsers() {
        assertNotEquals(Metrics.attemptedUser("Bearer rhak_one").orElseThrow(), Metrics.attemptedUser("Bearer rhak_two").orElseThrow());
    }

    @Test
    public void noCredentialsNoUser() {
        assertTrue(Metrics.attemptedUser("Bearer").isEmpty());
        assertTrue(Metrics.attemptedUser("Basic ").isEmpty());
        assertTrue(Metrics.attemptedUser("Negotiate abc").isEmpty());
    }
}
