/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.security.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.restheart.metrics.Metrics;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * The element of X-Forwarded-For the guard counts on: the documented key, the one read until
 * 9.9.1, neither, and the overrides for a server with more than one entry.
 */
public class BruteForceAttackGuardTest {
    @Test
    public void documentedKey() {
        assertEquals(1, BruteForceAttackGuard.xffValueFromLast(Map.of("x-forwarded-for-value-from-last-element", 1)));
    }

    @Test
    public void keyReadUntil991() {
        assertEquals(2, BruteForceAttackGuard.xffValueFromLast(Map.of("x-forwarded-for-value-from-last", 2)));
    }

    @Test
    public void documentedKeyFirst() {
        assertEquals(1, BruteForceAttackGuard.xffValueFromLast(Map.of(
                "x-forwarded-for-value-from-last-element", 1,
                "x-forwarded-for-value-from-last", 2)));
    }

    @Test
    public void neitherIsTheLastElement() {
        assertEquals(0, BruteForceAttackGuard.xffValueFromLast(Map.of("trust-x-forwarded-for", true)));
    }

    // two entries, as Ulabase during the switch: *.ulabase.app through CloudFront and the ALB, so the
    // client is the second to last; the old hosts straight to the ALB, the client is the last
    private static final Map<String, Object> TWO_ENTRIES = Map.of(
            "x-forwarded-for-value-from-last-element", 0,
            "x-forwarded-for-overrides", List.of(Map.of(
                    "predicate", "regex[pattern='.*\\.ulabase\\.app(:\\d+)?', value='%{i,Host}', full-match=true]",
                    "value-from-last-element", 1)));

    @AfterEach
    public void reset() {
        Metrics.xffOverrides(List.of());
        Metrics.xffValueRIndex(0);
    }

    @Test
    public void overrideByHost() {
        Metrics.xffValueRIndex(BruteForceAttackGuard.xffValueFromLast(TWO_ENTRIES));
        Metrics.xffOverrides(BruteForceAttackGuard.xffOverrides(TWO_ENTRIES, getClass().getClassLoader()));

        var xff = "6.6.6.6, 203.0.113.7, 10.0.1.5";
        assertEquals("203.0.113.7", Metrics.xffValue(xff, Metrics.xffValueRIndex(exchange("100a3f.ulabase.app"))));
        assertEquals("10.0.1.5", Metrics.xffValue(xff, Metrics.xffValueRIndex(exchange("100a3f.eu-central-1-free-1.restheart.com"))));
    }

    @Test
    public void wrongOverridesAreLeftOut() {
        var config = Map.<String, Object>of("x-forwarded-for-overrides", List.of(
                Map.of("predicate", "not a predicate[", "value-from-last-element", 1),
                Map.of("predicate", "true"),
                Map.of("predicate", "true", "value-from-last-element", -1)));
        assertEquals(0, BruteForceAttackGuard.xffOverrides(config, getClass().getClassLoader()).size());
    }

    private static HttpServerExchange exchange(String host) {
        var exchange = new HttpServerExchange();
        exchange.getRequestHeaders().put(Headers.HOST, host);
        return exchange;
    }
}
