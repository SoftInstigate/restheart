/*-
 * ========================LICENSE_START=================================
 * restheart-ai
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.ai.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.plugins.mcp.McpResource;
import org.restheart.plugins.security.DescriptorAuthorization;
import org.restheart.plugins.security.DescriptorAwareAuthorizer.Decision;
import org.restheart.plugins.security.RequestDescriptor;

/**
 * A catalogue is composed without arguments, and a call carries them. Deciding a call by the
 * catalogue's question is therefore wrong whenever a rule reads one: the resource looks absent to
 * the very caller holding what would have opened it.
 *
 * <p>The fixture is an ACL of exactly that shape — RESTHeart's own predicate language reads a
 * query parameter with {@code %{q,name}}, and the market-game example authorizes on two of them.
 */
class CatalogVisibilityTest {

    /** Allows the write only when it carries trader=trader1. */
    private static final DescriptorAuthorization PAIRED = descriptor -> {
        var trader = descriptor.queryParameters().get("trader");
        var carriesThePair = trader != null && "trader1".equals(trader.peekFirst());
        return carriesThePair ? Decision.allowed(null, null) : Decision.DENIED;
    };

    private static final RequestDescriptor IDENTITY =
            new RequestDescriptor(null, "GET", "/", Map.of(), Map.of(), Map.of(), "127.0.0.1", "https", Map.of());

    private static McpResource ledger() {
        return McpResource.builder()
                .uri("https://host/market_events")
                .action("query", a -> a.method("GET").readable(true))
                .action("create", a -> a.method("POST"))
                .build();
    }

    @Test
    @DisplayName("an action whose rule reads a query parameter is invokable when the call carries it")
    void canInvokeWithTheArgument() {
        assertTrue(CatalogVisibility.canInvoke(PAIRED, IDENTITY, ledger(), "create",
                Map.of("trader", "trader1", "secret", "s", "body", Map.of("type", "offer"))));
    }

    @Test
    @DisplayName("and refused when it carries the wrong one, or none")
    void cannotInvokeWithoutIt() {
        assertFalse(CatalogVisibility.canInvoke(PAIRED, IDENTITY, ledger(), "create", Map.of("trader", "trader2")));
        assertFalse(CatalogVisibility.canInvoke(PAIRED, IDENTITY, ledger(), "create", Map.of()));
    }

    @Test
    @DisplayName("an action the resource does not declare is never invokable")
    void unknownActionIsNotInvokable() {
        assertFalse(CatalogVisibility.canInvoke(PAIRED, IDENTITY, ledger(), "delete", Map.of("trader", "trader1")));
    }

    @Test
    @DisplayName("the listing, which has no arguments, leaves that action out — and that is not a refusal to call it")
    void theListingCannotKnow() {
        var invokable = CatalogVisibility.invokableActions(PAIRED, IDENTITY, ledger());

        assertFalse(invokable.contains("create"),
                "a listing is composed without arguments, so it cannot claim the action is available");
        assertTrue(CatalogVisibility.canInvoke(PAIRED, IDENTITY, ledger(), "create", Map.of("trader", "trader1")),
                "being left out of the listing must not make the call itself unreachable");
    }
}
