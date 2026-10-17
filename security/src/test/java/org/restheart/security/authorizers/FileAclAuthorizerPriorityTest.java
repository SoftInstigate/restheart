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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.security.BaseAclPermission;

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;

/**
 * The order fileAclAuthorizer evaluates the permissions of a role in: the highest priority first,
 * as documented and as mongoAclAuthorizer does (#764).
 */
class FileAclAuthorizerPriorityTest {
    private static final String USER = "user";
    private static final String PATCH_DOCUMENT = "path-template('/db/collection/{id}') and method(PATCH)";

    @Test
    void theHighestPriorityPermissionOfARoleWins() throws Exception {
        var general = permission(100);
        var specific = permission(200);

        // the general rule first in the file, as a user would write it, and the exception after it
        var request = request(List.of(USER));
        assertTrue(authorizer(List.of(general, specific)).isAllowed(request));
        assertSame(specific, BaseAclPermission.getRaw(request), "priority 200 must beat priority 100");

        // the same ACL with the entries swapped: the position in the file must not matter
        request = request(List.of(USER));
        assertTrue(authorizer(List.of(specific, general)).isAllowed(request));
        assertSame(specific, BaseAclPermission.getRaw(request), "priority 200 must beat priority 100");
    }

    @Test
    void aPermissionWithoutPriorityIsConsideredLast() throws Exception {
        var withoutPriority = permission(null);
        var lowest = permission(0);

        var request = request(List.of(USER));
        assertTrue(authorizer(List.of(withoutPriority, lowest)).isAllowed(request));
        assertSame(lowest, BaseAclPermission.getRaw(request), "priority 0 must beat a missing priority");

        assertEquals(Integer.MIN_VALUE, FileAclPermission.build(withoutPriority).getPriority());
    }

    @Test
    void aMongoPermissionWithoutPriorityIsConsideredLast() {
        var doc = BsonDocument.parse("""
                {"_id": "p", "roles": ["user"], "predicate": "path-prefix('/')"}
                """);

        assertEquals(Integer.MIN_VALUE, MongoAclPermission.build(doc).getPriority());
    }

    private static Map<String, Object> permission(Integer priority) {
        // a mutable map: a null priority means no priority at all
        var p = new HashMap<String, Object>();
        p.put("roles", List.of(USER));
        p.put("predicate", PATCH_DOCUMENT);
        if (priority != null) {
            p.put("priority", priority);
        }
        p.put("mongo", Map.of("allowWriteMode", false));
        return p;
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
