/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.utils;

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
 * Tests for {@link CustomOperatorRegistryImpl}, independent of {@link VarsInterpolator}'s
 * dispatch (covered separately in {@code VarsInterpolatorCustomOperatorTest}).
 */
class CustomOperatorRegistryImplTest {

    private static CustomOperator namedOperator(String name) {
        return new CustomOperator() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public BsonValue resolve(Request<?> request, BsonValue arg) {
                return new BsonString("x");
            }
        };
    }

    @Test
    void noBuiltInOperatorsArePreRegistered() {
        // unlike AclVarsRegistryImpl, $var/$arg are handled directly by VarsInterpolator,
        // not through this registry — there is nothing to pre-register.
        var name = "doesnotexist" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(CustomOperatorRegistryImpl.getInstance().operator(name).isEmpty());
    }

    @Test
    void registeringANewNameSucceedsAndBecomesResolvable() throws ConfigurationException {
        var name = "test" + UUID.randomUUID().toString().replace("-", "");
        var op = namedOperator(name);

        CustomOperatorRegistryImpl.getInstance().register(op);

        assertEquals(op, CustomOperatorRegistryImpl.getInstance().operator(name).orElseThrow());
        assertTrue(CustomOperatorRegistryImpl.getInstance().names().contains(name));
    }

    @Test
    void registeringAnAlreadyTakenNameFails() throws ConfigurationException {
        var name = "test" + UUID.randomUUID().toString().replace("-", "");

        CustomOperatorRegistryImpl.getInstance().register(namedOperator(name));

        assertThrows(ConfigurationException.class, () -> CustomOperatorRegistryImpl.getInstance().register(namedOperator(name)));
    }

    @Test
    void reservedNamesCannotBeRegistered() {
        assertThrows(ConfigurationException.class, () -> CustomOperatorRegistryImpl.getInstance().register(namedOperator("var")));
        assertThrows(ConfigurationException.class, () -> CustomOperatorRegistryImpl.getInstance().register(namedOperator("arg")));
    }

    @Test
    void unknownNameResolvesToEmpty() {
        var name = "doesnotexist" + UUID.randomUUID().toString().replace("-", "");

        assertTrue(CustomOperatorRegistryImpl.getInstance().operator(name).isEmpty());
    }
}
