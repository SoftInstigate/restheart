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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.security.BaseAclPermission;

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;

class FileAclAuthorizerTest {
    private static final String ADMIN = "admin";
    private static final String USER = "user";
    private static final String PATCH_DOCUMENT =
            "path-template('/db/collection/{id}') and method(PATCH)";

    @Test
    void highestPriorityPermissionWinsRegardlessOfRoleOrder() throws Exception {
        var admin = permission(ADMIN, 110, Map.of(
                "allowManagementRequests", false, "allowWriteMode", false));
        var user = permission(USER, 112, Map.of(
                "allowManagementRequests", false, "allowWriteMode", false,
                "writeFilter", "{\"owner\": \"@user.userid\"}"));
        var authorizer = authorizer(List.of(admin, user));

        for (var roles : List.of(List.of(ADMIN), List.of(ADMIN, USER), List.of(USER, ADMIN))) {
            var request = request(roles);
            assertTrue(authorizer.isAllowed(request));
            assertSame(admin, BaseAclPermission.getRaw(request), "Wrong Mongo permission for " + roles);
        }

        var userRequest = request(List.of(USER));
        assertTrue(authorizer.isAllowed(userRequest));
        assertSame(user, BaseAclPermission.getRaw(userRequest));
    }

    private static Map<String, Object> permission(String role, int priority, Map<String, Object> mongo) {
        return Map.of("roles", List.of(role), "predicate", PATCH_DOCUMENT,
                "priority", priority, "mongo", mongo);
    }

    private static FileAclAuthorizer authorizer(List<Map<String, Object>> permissions) throws Exception {
        var authorizer = new FileAclAuthorizer();
        var config = FileAclAuthorizer.class.getDeclaredField("config");
        config.setAccessible(true);
        config.set(authorizer, Map.of("permissions", permissions));
        var registry = FileAclAuthorizer.class.getDeclaredField("registry");
        registry.setAccessible(true);
        registry.set(authorizer, mock(PluginsRegistry.class));
        authorizer.init();
        return authorizer;
    }

    private static Request<?> request(List<String> roles) {
        var exchange = spy(new HttpServerExchange());
        exchange.setRequestPath("/db/collection/doc-1");
        exchange.setRequestMethod(Methods.PATCH);
        var context = mock(SecurityContext.class);
        var account = mock(Account.class);
        when(account.getRoles()).thenReturn(new LinkedHashSet<>(roles));
        when(context.getAuthenticatedAccount()).thenReturn(account);
        when(exchange.getSecurityContext()).thenReturn(context);
        var request = mock(Request.class);
        when(request.getExchange()).thenReturn(exchange);
        return request;
    }
}
