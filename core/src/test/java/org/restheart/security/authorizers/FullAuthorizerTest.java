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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.restheart.plugins.security.DescriptorAwareAuthorizer;
import org.restheart.plugins.security.RequestDescriptor;

/**
 * Regression test for restheart#722: a {@code secured = false} service auto-gets {@link
 * FullAuthorizer} as its sole ALLOWER (see {@code PluginsRegistryImpl.plugService}) specifically
 * so nothing is restricted for it — {@link FullAuthorizer} must honor that same "allow everything"
 * contract for {@link DescriptorAwareAuthorizer}-based checks too, or a service marked unsecured
 * would still fail closed on any {@code operationsToAuthorize()}-derived operation.
 */
public class FullAuthorizerTest {

    @Test
    public void isDescriptorAware() {
        assertTrue(new FullAuthorizer(false) instanceof DescriptorAwareAuthorizer);
    }

    @Test
    public void allowsAnyDescriptor() {
        var authorizer = new FullAuthorizer(false);
        var descriptor = new RequestDescriptor(null, "GET", "/warehouse/inventory", Map.of(), Map.of(), Map.of(), null, null);

        assertTrue(authorizer.isAllowed(descriptor));
    }
}
