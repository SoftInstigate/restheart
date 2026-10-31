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

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Request;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.security.BaseAclPermission;

import com.mongodb.client.MongoClient;

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;

class MongoAclAuthorizerTest {
    @Test
    void highestNumericPriorityWinsRegardlessOfRoleOrder() throws Exception {
        var admin = BsonDocument.parse("""
                {"_id": "admin", "roles": ["admin"], "priority": 112,
                 "predicate": "path-template('/db/collection/{id}') and method(PATCH)",
                 "mongo": {}}
                """);
        var user = BsonDocument.parse("""
                {"_id": "user", "roles": ["user"], "priority": 110,
                 "predicate": "path-template('/db/collection/{id}') and method(PATCH)",
                 "mongo": {"writeFilter": {"owner": "@user.userid"},
                           "readFilter": {"owner": "@user.userid"}}}
                """);
        var client = mock(MongoClient.class, RETURNS_DEEP_STUBS);
        var collection = client.getDatabase("db").getCollection("acl").withDocumentClass(BsonDocument.class);
        for (var permission : List.of(admin, user)) {
            when(collection.find(eq("roles", permission.getString("_id").getValue()))
                    .sort(any()).into(anyCollection())).thenAnswer(invocation -> {
                Collection<BsonDocument> result = invocation.getArgument(0);
                result.add(permission);
                return result;
            });
        }

        var authorizer = new MongoAclAuthorizer();
        authorizer.aclDb = "db";
        authorizer.aclCollection = "acl";
        for (var injection : Map.of("mclient", client, "registry", mock(PluginsRegistry.class)).entrySet()) {
            var field = MongoAclAuthorizer.class.getDeclaredField(injection.getKey());
            field.setAccessible(true);
            field.set(authorizer, injection.getValue());
        }

        for (var roles : List.of(List.of("admin"), List.of("admin", "user"), List.of("user", "admin"))) {
            var request = request(roles);
            assertTrue(authorizer.isAllowed(request));
            assertSame(admin, BaseAclPermission.getRaw(request), "Wrong Mongo permission for " + roles);
        }

        var userRequest = request(List.of("user"));
        assertTrue(authorizer.isAllowed(userRequest));
        assertSame(user, BaseAclPermission.getRaw(userRequest));
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
