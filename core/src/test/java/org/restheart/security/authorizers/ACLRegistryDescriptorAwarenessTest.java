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
package org.restheart.security.authorizers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.DescriptorAwareAuthorizer;
import org.restheart.plugins.security.RequestDescriptor;

/**
 * The two registry authorizers must be consulted when authorization is asked about a descriptor,
 * not only about a real request.
 *
 * <p>They were not, and it was invisible: {@code DescriptorAuthorizationImpl} silently skips any
 * authorizer that does not implement {@link DescriptorAwareAuthorizer}, so a deployment granting
 * access from code — an interceptor registering an allow predicate, which is how a multi-tenant
 * host authorizes its own administrators — was allowed on every real request and denied on every
 * descriptor. The MCP catalogue asks by descriptor, so such a caller was served an empty listing of
 * resources it could read in full. The vetoer's half fails the other way, and worse: a veto that is
 * never consulted lets a listing advertise what a read would refuse.
 *
 * <p>Asserted at the level the defect lived at — whether the two are picked up at all. Exercising
 * their predicates would mean building a real {@code HttpServerExchange}, which no unit test in
 * this project can do: {@code commons} ships a hand-written {@code io.undertow.server
 * .HttpServerExchange} in its test sources, and it shadows the real one wherever those test classes
 * reach. That half is covered end to end by the cloud's {@code mcp-scope} scenario.
 */
public class ACLRegistryDescriptorAwarenessTest {

    private static PluginRecord<Authorizer> record(String name, Authorizer instance) {
        return new PluginRecord<>(name, name, false, true, instance.getClass().getName(), instance, Map.of());
    }

    @Test
    public void bothRegistryAuthorizersDecideAboutDescriptors() {
        assertTrue(new ACLRegistryAllower() instanceof DescriptorAwareAuthorizer,
                "an allow predicate must reach a descriptor, or a code-granted permission is invisible to one");
        assertTrue(new ACLRegistryVetoer() instanceof DescriptorAwareAuthorizer,
                "a veto predicate must reach a descriptor, or a listing can advertise what a read refuses");
    }

    @Test
    public void aPlainAuthorizerIsSkipped_aDescriptorAwareOneIsAsked() {
        // The rule that dropped the two: an authorizer that does not implement
        // DescriptorAwareAuthorizer is not consulted at all — not asked and told no, simply absent.
        // Both stubs here would allow everything if they were asked.
        var plain = new DescriptorAuthorizationImpl(List.of(record("plain", allowEverythingPlain())));
        assertFalse(plain.isAllowed(descriptor()), "a plain Authorizer never reaches a descriptor");

        var aware = new DescriptorAuthorizationImpl(List.of(record("aware", allowEverythingAware())));
        assertTrue(aware.isAllowed(descriptor()), "a descriptor-aware one does");
    }

    @Test
    public void withNoDescriptorAwareAuthorizerAtAllEverythingIsDenied() {
        assertFalse(new DescriptorAuthorizationImpl(List.of()).isAllowed(descriptor()));
    }

    /** Allows everything, the old way — the shape the two registry authorizers used to have. */
    private static Authorizer allowEverythingPlain() {
        return new Authorizer() {
            @Override
            public boolean isAllowed(org.restheart.exchange.Request<?> request) {
                return true;
            }

            @Override
            public boolean isAuthenticationRequired(org.restheart.exchange.Request<?> request) {
                return false;
            }
        };
    }

    /** The same, the way they are now. */
    private static Authorizer allowEverythingAware() {
        return new DescriptorAwareAuthorizer() {
            @Override
            public boolean isAllowed(org.restheart.exchange.Request<?> request) {
                return true;
            }

            @Override
            public boolean isAuthenticationRequired(org.restheart.exchange.Request<?> request) {
                return false;
            }

            @Override
            public Decision decide(RequestDescriptor d) {
                return Decision.allowed(null, null);
            }
        };
    }

    private static RequestDescriptor descriptor() {
        return new RequestDescriptor(null, "GET", "/inventory", Map.of(), Map.of(), Map.of(), null, null, Map.of());
    }
}
