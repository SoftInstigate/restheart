/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.bson.BsonString;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.Request;

/**
 * Tests for {@link AclVarsRegistryImpl}, independent of {@link AclVarsInterpolator}'s dispatch
 * (covered separately in {@code AclVarsInterpolatorTest}).
 */
class AclVarsRegistryImplTest {

    private static VarResolver namedResolver(String name) {
        return new VarResolver() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public BsonValue resolve(Request<?> request, String var) {
                return new BsonString("x");
            }
        };
    }

    @Test
    void builtInResolversArePreRegistered() {
        var names = AclVarsRegistryImpl.getInstance().names();

        assertTrue(names.contains("user"));
        assertTrue(names.contains("request"));
        assertTrue(names.contains("filter"));
        assertTrue(names.contains("now"));
        assertTrue(names.contains("mongoPermissions"));
        assertTrue(names.contains("rnd"));
        assertTrue(names.contains("qparams"));
    }

    @Test
    void registeringANewNameSucceedsAndBecomesResolvable() throws ConfigurationException {
        var name = "test" + UUID.randomUUID().toString().replace("-", "");
        var resolver = namedResolver(name);

        AclVarsRegistryImpl.getInstance().register(resolver);

        assertEquals(resolver, AclVarsRegistryImpl.getInstance().resolver(name).orElseThrow());
        assertTrue(AclVarsRegistryImpl.getInstance().names().contains(name));
    }

    @Test
    void registeringAnAlreadyTakenNameFails() throws ConfigurationException {
        var name = "test" + UUID.randomUUID().toString().replace("-", "");

        AclVarsRegistryImpl.getInstance().register(namedResolver(name));

        assertThrows(ConfigurationException.class, () -> AclVarsRegistryImpl.getInstance().register(namedResolver(name)));
    }

    @Test
    void builtInNamesCannotBeOverridden() {
        assertThrows(ConfigurationException.class, () -> AclVarsRegistryImpl.getInstance().register(namedResolver("rnd")));
    }

    @Test
    void unknownNameResolvesToEmpty() {
        var name = "doesnotexist" + UUID.randomUUID().toString().replace("-", "");

        assertTrue(AclVarsRegistryImpl.getInstance().resolver(name).isEmpty());
    }
}
