package org.restheart.security.predicates;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.restheart.exchange.MongoRequest;
import org.restheart.security.AclVarsInterpolator;

import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;

/**
 * `@roles` used the way a rule uses it: inside `in`, in this module, where `in`
 * is registered.
 *
 * <p>Worth its own test because the chain has three links and the failure of any
 * one of them looks identical from outside — the rule simply does not match.
 * The resolver is covered in `commons`; this covers the two that follow it, the
 * substitution into predicate text and the parse.
 *
 * <p>`in`'s `array` is a `String[]` fixed when the predicate is built, not an
 * attribute read per request, so this only works because the interpolator
 * rewrites the predicate *text* first — turning `array=@roles` into
 * `array={'$unauthenticated'}`. Nothing else in the chain would notice if that
 * stopped: an unresolved variable is replaced by a random token, so the
 * predicate still parses and quietly compares against nonsense.
 */
public class RolesInPredicateTest {

    private static final String ANONYMOUS_CHECK = "equals(@authenticated, 'false')";

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

    private static boolean matches(MongoRequest request, String predicate) {
        // `in`'s `value` is an ExchangeAttribute and reads from the exchange, so
        // this needs one even when the value is a constant.
        return AclVarsInterpolator
                .interpolatePredicate(request, predicate, RolesInPredicateTest.class.getClassLoader())
                .resolve(mock(HttpServerExchange.class));
    }

    @Test
    public void anAnonymousRequestMatchesTheAnonymousCheck() {
        assertTrue(matches(anonymous(), ANONYMOUS_CHECK));
    }

    @Test
    public void theRoleNameCannotBeCompared() {
        // Why @authenticated exists at all. `$` is Undertow's sigil for an
        // exchange attribute, so '$unauthenticated' in a value position is read
        // as an attribute of that name, resolves to nothing, and matches
        // nothing — for an anonymous request too, which is precisely when a rule
        // guarded that way needs it to match.
        assertFalse(matches(anonymous(), "in(value='$unauthenticated', array=@roles)"));
    }

    @Test
    public void aSignedInRequestDoesNot() {
        assertFalse(matches(signedInAs("user"), ANONYMOUS_CHECK));
    }

    @Test
    public void aRuleCanBeScopedToSignedInCallers() {
        // The form a guard condition uses: guard the rule, then say what it is
        // about. Written out because getting the negation the wrong way round
        // is how a rule silently applies to everyone.
        var scoped = "not " + ANONYMOUS_CHECK;

        assertTrue(matches(signedInAs("user"), scoped));
        assertFalse(matches(anonymous(), scoped));
    }

    @Test
    public void aRuleCanBeScopedToOneRole() {
        assertTrue(matches(signedInAs("user", "staff"), "in(value='staff', array=@roles)"));
        assertFalse(matches(signedInAs("user"), "in(value='staff', array=@roles)"));
        assertFalse(matches(anonymous(), "in(value='staff', array=@roles)"));
    }
}
