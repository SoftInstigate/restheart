/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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
package org.restheart.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.restheart.configuration.Utils.overrides;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ConfigurationUtilsTest {
    @Test
    public void testEmpty() {
        var os = overrides("   ");

        assertEquals(os.size(), 0);
    }

    @Test
    public void testOne() {
        var os = overrides("/a/b/c->1");

        assertEquals(os.size(), 1);

        var o = os.get(0);
        assertEquals(o.path(), "/a/b/c");
        assertEquals(1, o.value());
    }

    @Test
    public void testNoOperator() {
        assertThrows(IllegalArgumentException.class, () -> overrides("/a/b/c"));
    }

    @Test
    public void testNoPath() {
        assertThrows(IllegalArgumentException.class, () -> overrides("->1"));
    }

    @Test
    public void testWrongPath() {
        assertThrows(IllegalArgumentException.class, () -> overrides("/@@@@->1"));
        assertThrows(IllegalArgumentException.class, () -> overrides("/---->1"));
    }

    @Test
    public void testWrongValue() {
        assertThrows(IllegalArgumentException.class, () -> overrides("/a->[1,2"));
    }

    @Test
    public void testPathNotAbsolute() {
        assertThrows(IllegalArgumentException.class, () -> overrides("a->[1,2"));
    }

    @Test
    public void testSplit() {
        var rho = "/a/b/c->{\"k\": \"v;\"};/a/b/c->{\"k\": \"v\"};/a->1;/a->\"1\";";

        var os = overrides(rho);

        assertEquals(os.size(), 4);
    }

    @Test
    public void testMap() {
        var o = overrides("/a->{'a': 1, 'b': 2}").get(0);

        assertTrue(o.value() instanceof Map<?, ?>);
    }

    @Test
    public void testList() {
        var o = overrides("/a->[1,2,3]").get(0);

        assertTrue(o.value() instanceof List<?>);
    }

    @Test
    public void testStringSingleQuote() {
        var o = overrides("/a->'ciao;';/b->'eccolo;'").get(0);

        System.out.println("*********** " + o.value());

        assertTrue(o.value() instanceof String);
        if (o.value() instanceof String v) {
            assertTrue(v.endsWith(";"));
        }
    }

    @Test
    public void testStringSingleQuoteWithEscaped() {
        var o = overrides("/a->'ciao;';/b->'eccolo \\'qui;\\''").get(0);

        assertTrue(o.value() instanceof String);
        if (o.value() instanceof String v) {
            assertTrue(v.endsWith(";"));
        }
    }

    @Test
    public void testStringDoubleQuote() {
        var o = overrides("/a->\"ciao\";/b->\"eccolo;\"").get(0);

        assertTrue(o.value() instanceof String);
    }

    @Test
    public void testStringDoubleQuoteWithEscaped() {
        var o = overrides("/a->\"ciao\";/b->\"eccolo \\\"qui;\\\" \"").get(0);

        assertTrue(o.value() instanceof String);
    }

    @Test
    public void testInt() {
        var o = overrides("/a->0").get(0);

        assertTrue(o.value() instanceof Integer);
    }
}
