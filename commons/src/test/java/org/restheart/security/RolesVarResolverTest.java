package org.restheart.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.MongoRequest;

import io.undertow.security.idm.Account;

/**
 * What {@code @roles} resolves to, and — the part that actually bit — what it
 * becomes once interpolated into a predicate string.
 *
 * <p>Written after an afternoon of probing a live service to find out whether
 * the variable worked, which a test answers in a second and keeps answering.
 * The black box could only ever say "the rule did not match"; it could not say
 * which of the three links in the chain was broken — the resolver, the
 * substitution, or the predicate.
 */
public class RolesVarResolverTest {

    private static MongoRequest anonymous() {
        var request = mock(MongoRequest.class);
        when(request.isAuthenticated()).thenReturn(false);
        return request;
    }

    private static MongoRequest signedInAs(String... roles) {
        var request = mock(MongoRequest.class);
        var account = mock(Account.class);
        when(request.isAuthenticated()).thenReturn(true);
        when(request.getAuthenticatedAccount()).thenReturn(account);
        when(account.getRoles()).thenReturn(Set.of(roles));
        return request;
    }

    private static org.bson.BsonValue resolve(MongoRequest request) {
        return AclVarsInterpolator.resolveVar(request, "@roles");
    }

    @Test
    public void anonymousResolvesToTheUnauthenticatedRole() {
        var value = resolve(anonymous());

        assertTrue(value.isArray(), "@roles must be an array, or it cannot be an `in` predicate's array");
        assertEquals(1, value.asArray().size());
        assertEquals("$unauthenticated", value.asArray().get(0).asString().getValue());
    }

    @Test
    public void anAccountResolvesToItsRoles() {
        var value = resolve(signedInAs("user", "staff"));

        assertTrue(value.isArray());
        assertEquals(2, value.asArray().size());
        assertTrue(value.asArray().stream()
                .map(v -> v.asString().getValue())
                .toList()
                .containsAll(Set.of("user", "staff")));
    }

    @Test
    public void anAccountWithNoRolesIsNotAnonymous() {
        // An API key naming no roles is still a credential. A rule written for
        // guests must not apply to it.
        var value = resolve(signedInAs());

        assertTrue(value.isArray());
        assertTrue(value.asArray().isEmpty());
    }

    // The two predicate tests live in the `security` module: `in` is registered
    // there, and in this module's test classpath it does not exist at all —
    // which is its own small lesson, since a predicate that is merely absent
    // fails the same way as one that is wrong.
}
