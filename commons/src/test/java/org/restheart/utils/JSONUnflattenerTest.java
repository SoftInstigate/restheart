/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
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

import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;

import org.bson.BsonNull;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JSONUnflattenerTest {

    private static final Logger LOG = LoggerFactory.getLogger(JSONUnflattenerTest.class);

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    public JSONUnflattenerTest() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testDocument() {
        var source = BsonUtils.parse(
        """
            {
                "a": 1,
                "b.c": 2,
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains a", result.asDocument().containsKey("a"));

        assertTrue("check result contains b", result.asDocument().containsKey("b"));
        assertTrue("check result contains b as document", result.asDocument().get("b").isDocument());

        assertTrue("check result contains b.c", result.asDocument().get("b").asDocument().containsKey("c"));
        assertTrue("check result contains b.c as number", result.asDocument().get("b").asDocument().get("c").isNumber());
    }

    @Test
    public void testNumerKey() {
        var source = BsonUtils.parse(
        """
            {
                "0": "a",
                "1": "b",
                "2": "c"
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains 0", result.asDocument().containsKey("0"));
        assertTrue("check result contains 1", result.asDocument().containsKey("1"));
        assertTrue("check result contains 2", result.asDocument().containsKey("2"));
    }

    @Test
    public void testNumerKeyAndArray() {
        var source = BsonUtils.parse(
        """
            {
                "0": "a",
                "1": "b",
                "2": "c",
                "a.0": "d",
                "a.1": "e",
                "a.2": "f"
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains 0", result.asDocument().containsKey("0"));
        assertTrue("check result contains 1", result.asDocument().containsKey("1"));
        assertTrue("check result contains 2", result.asDocument().containsKey("2"));
        assertTrue("check result contains a", result.asDocument().containsKey("a"));
        assertTrue("check result contains a as array", result.asDocument().get("a").isArray());
    }

    @Test
    public void testObjectAndFieldWithDotNotation() {
        var source = BsonUtils.parse(
        """
            {
                "x": {
                    "a": "a",
                    "b": "b",
                    "c": "c"
                },
                "x.d": "d"
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains x", result.asDocument().containsKey("x"));
        assertTrue("check result contains x as document", result.asDocument().get("x").isDocument());
        assertTrue("check result contains x.d", result.asDocument().get("x").asDocument().containsKey("d"));
        assertTrue("check result contains x as document with 4 props", result.asDocument().get("x").asDocument().size() == 4);
    }

    @Test
    public void testObjectWithNumericKeyFieldWithDotNotation() {
        var source = BsonUtils.parse(
        """
            {
                "0": {
                    "a": "a",
                    "b": "b",
                    "c": "c"
                },
                "0.d": "d"
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains 0", result.asDocument().containsKey("0"));
        assertTrue("check result contains 0 as document", result.asDocument().get("0").isDocument());
        assertTrue("check result contains 0.d", result.asDocument().get("0").asDocument().containsKey("d"));
        assertTrue("check result contains 0 as document with 4 props", result.asDocument().get("0").asDocument().size() == 4);
    }

    @Test
    public void testNestedObjectWithDotNotation() {
        var source = BsonUtils.parse(
        """
            {
                "sub": {
                    "x": {
                        "a": "a",
                        "b": "b",
                        "c": "c"
                    }
                },
                "sub.x.d": "d"
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains sub", result.asDocument().containsKey("sub"));
        assertTrue("check result contains sub as document", result.asDocument().get("sub").isDocument());
        var sub =  result.asDocument().get("sub").asDocument();
        assertTrue("check sub contains x as document", sub.get("x").isDocument());
        assertTrue("check result contains x.d", sub.get("x").asDocument().containsKey("d"));
        assertTrue("check result contains x as document with 4 props", sub.get("x").asDocument().size() == 4);
    }

    @Test
    public void testArray() {
        var source = BsonUtils.parse(
        """
            {
                "array": [ "bar", "foo", null, null, "zoo"]
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains array", result.asDocument().containsKey("array"));
        assertTrue("check result contains array as array", result.asDocument().get("array").isArray());
        var array = result.asDocument().get("array").asArray();
        assertEquals("check result array contains five elements", 5, array.size());
        assertEquals("check result 2nd element of array is null", BsonNull.VALUE, array.get(2));
        assertEquals("check result 3rd element of array is null", BsonNull.VALUE, array.get(3));
        assertTrue("check result second element of array is string", array.get(1).isString());
        assertTrue("check result first element of array is string", array.get(0).isString());
    }

    @Test
    public void testArrayDotNotationLeaf() {
        var source = BsonUtils.parse(
        """
            {
                "a": 1,
                "b.c.0": 1,
                "b.c.1": 2,
                "b.c.2": 3
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains a", result.asDocument().containsKey("a"));

        assertTrue("check result contains b", result.asDocument().containsKey("b"));
        assertTrue("check result b is document", result.asDocument().get("b").isDocument());

        assertTrue("check result contains b.c", result.asDocument().get("b").asDocument().containsKey("c"));
        assertTrue("check result contains b.c as array", result.asDocument().get("b").asDocument().get("c").isArray());
        assertTrue("check result b.c size is 3", result.asDocument().get("b").asDocument().get("c").asArray().size() == 3);
    }

    @Test
    public void testArrayWithDotNotation() {
        var source = BsonUtils.parse(
        """
            {
                "array.1": "bar",
                "array.0": "foo",
                "array.4": "zoo"
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains array", result.asDocument().containsKey("array"));
        assertTrue("check result contains array as array", result.asDocument().get("array").isArray());
        var array = result.asDocument().get("array").asArray();
        assertEquals("check result array contains five elements", 5, array.size());
        assertEquals("check result 2nd element of array is null", BsonNull.VALUE, array.get(2));
        assertEquals("check result 3rd element of array is null", BsonNull.VALUE, array.get(3));
        assertTrue("check result second element of array is string", array.get(1).isString());
        assertTrue("check result first element of array is string", array.get(0).isString());
    }

    @Test
    public void testArrayOfDocumentsWithDotNotation() {
        var source = BsonUtils.parse(
        """
            {
                "array.0.s": "foo",
                "array.1.s": "bar",
                "array.4.s": "zoo"
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains array", result.asDocument().containsKey("array"));
        assertTrue("check result contains array as array", result.asDocument().get("array").isArray());
        var array = result.asDocument().get("array").asArray();
        assertEquals("check result array contains five elements", 5, array.size());
        assertEquals("check result 2nd element of array is null", BsonNull.VALUE, array.get(2));
        assertEquals("check result 3rd element of array is null", BsonNull.VALUE, array.get(3));
        assertTrue("check result second element of array is document", array.get(1).isDocument());
        assertTrue("check result first element of array is document", array.get(0).isDocument());
    }

    @Test
    public void testNestedObjectAndWithNumericKeyFieldWithDotNotation() {
        var source = BsonUtils.parse(
        """
            {
                "sub": [
                    { "f": "a" },
                    { "f": "a" },
                    { "f": "a" },
                    { "f": "a" }
                ],
                "sub.0.f": "b"
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains sub", result.asDocument().containsKey("sub"));
        assertTrue("check result contains sub as array", result.asDocument().get("sub").isArray());
        var sub =  result.asDocument().get("sub").asArray();
        assertTrue("check sub contains 4 elements", sub.size() == 4);
        assertTrue("check sub first element is a document", sub.get(0).isDocument());
        assertTrue("check sub first element has f property", sub.get(0).asDocument().containsKey("f"));
        assertTrue("check sub first element has f property as string", sub.get(0).asDocument().get("f").isString());
        assertTrue("check sub first element has f property is 'b'", sub.get(0).asDocument().get("f").asString().getValue().equals("b"));
    }

    @Test
    public void testDotNotationInNestedPropery() {
        var source = BsonUtils.parse(
        """
            { "keys":
                { "ids.code": 1 }
            }
        """);

        var result = JsonUnflattener.unflatten(source);
        assertTrue("check result is document", result.isDocument());
        assertTrue("check result contains keys", result.asDocument().containsKey("keys"));
        assertTrue("check result contains keys as document", result.asDocument().get("keys").isDocument());
        assertTrue("check result contains keys as document with field 'ids.code'", result.asDocument().get("keys").asDocument().containsKey("ids.code"));
    }
}
