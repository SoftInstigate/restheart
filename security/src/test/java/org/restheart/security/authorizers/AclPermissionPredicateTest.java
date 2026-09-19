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
package org.restheart.security.authorizers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.configuration.ConfigurationException;

/**
 * A permission's predicate is parsed twice: when it loads, and again on every request with its
 * variables substituted. Only the second parse sees values, so the first must not require the
 * variables themselves to be valid predicate syntax — and {@code @qparams['key']} is not, because
 * square brackets are the predicate language's own parameter-list syntax.
 *
 * <p>Before this was fixed, such a permission was discarded at load with
 * {@code UT000045 Unexpected token}, and every request it should have authorized answered 403 for
 * a reason visible only in one line of the log.
 */
class AclPermissionPredicateTest {

    private static Map<String, Object> permission(String predicate) {
        return Map.of("role", "user", "predicate", predicate, "priority", 100);
    }

    @Test
    @DisplayName("a predicate reading a query parameter loads")
    void qparamsInAPredicateLoads() {
        assertNotNull(FileAclPermission.build(permission(
                "path('/products') and method(GET) and equals(@qparams['category'], @user.category)")));
    }

    @Test
    @DisplayName("the double-quoted form loads too")
    void qparamsWithDoubleQuotesLoads() {
        assertNotNull(FileAclPermission.build(permission(
                "path('/t') and method(GET) and equals(@qparams[\"token\"], 'abc')")));
    }

    @Test
    @DisplayName("every other variable still loads, alone and together")
    void otherVariablesLoad() {
        List.of("path('/x') and equals(@user._id, 'u')",
                "path('/x') and equals(@request.body.amount, '10')",
                "path('/x') and equals(@now, @rnd(32))",
                "path-template('/users/{userid}/verify') and method(PATCH) and equals(@user._id, ${userid}) and equals(@user.otp, @qparams['otp'])")
            .forEach(predicate -> assertNotNull(FileAclPermission.build(permission(predicate))));
    }

    @Test
    @DisplayName("a variable inside a quoted literal is left alone, and the predicate still loads")
    void aQuotedVariableIsNotAVariable() {
        assertNotNull(FileAclPermission.build(permission("path('/x') and equals('@user.name', 'literal')")));
    }

    @Test
    @DisplayName("Undertow's own query-parameter attribute keeps working")
    void exchangeAttributeLoads() {
        assertNotNull(FileAclPermission.build(permission(
                "path-prefix('/market_events') and method(POST) and equals(%{q,trader}, 'trader1')")));
    }

    @Test
    @DisplayName("a predicate that is genuinely broken is still refused")
    void aBrokenPredicateIsRefused() {
        assertThrows(ConfigurationException.class,
                () -> FileAclPermission.build(permission("path('/x') and equals(@qparams['a']")));
        assertThrows(ConfigurationException.class,
                () -> FileAclPermission.build(permission("nonsense(@user._id)")));
    }
}
