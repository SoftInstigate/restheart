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
package org.restheart.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonNull;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JSONUnflattenerTest {

    private static final Logger LOG = LoggerFactory.getLogger(JSONUnflattenerTest.class);

    // @Rule
    // public TestRule watcher = new TestWatcher() {
    // @Override
    // protected void starting(Description description) {
    // LOG.info("executing test {}", description.toString());
    // }
    // };
    public JSONUnflattenerTest() {
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("a"), "check result contains a");

        assertTrue(result.asDocument().containsKey("b"), "check result contains b");
        assertTrue(result.asDocument().get("b").isDocument(), "check result contains b as document");

        assertTrue(result.asDocument().get("b").asDocument().containsKey("c"), "check result contains b.c");
        assertTrue(
                result.asDocument().get("b").asDocument().get("c").isNumber(), "check result contains b.c as number");
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("0"), "check result contains 0");
        assertTrue(result.asDocument().containsKey("1"), "check result contains 1");
        assertTrue(result.asDocument().containsKey("2"), "check result contains 2");
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("0"), "check result contains 0");
        assertTrue(result.asDocument().containsKey("1"), "check result contains 1");
        assertTrue(result.asDocument().containsKey("2"), "check result contains 2");
        assertTrue(result.asDocument().containsKey("a"), "check result contains a");
        assertTrue(result.asDocument().get("a").isArray(), "check result contains a as array");
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("x"), "check result contains x");
        assertTrue(result.asDocument().get("x").isDocument(), "check result contains x as document");
        assertTrue(result.asDocument().get("x").asDocument().containsKey("d"), "check result contains x.d");
        assertTrue(
                result.asDocument().get("x").asDocument().size() == 4,
                "check result contains x as document with 4 props");
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("0"), "check result contains 0");
        assertTrue(result.asDocument().get("0").isDocument(), "check result contains 0 as document");
        assertTrue(result.asDocument().get("0").asDocument().containsKey("d"), "check result contains 0.d");
        assertTrue(result.asDocument().get("0").asDocument().size() == 4,
                "check result contains 0 as document with 4 props");
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("sub"), "check result contains sub");
        assertTrue(result.asDocument().get("sub").isDocument(), "check result contains sub as document");
        var sub = result.asDocument().get("sub").asDocument();
        assertTrue(sub.get("x").isDocument(), "check sub contains x as document");
        assertTrue(sub.get("x").asDocument().containsKey("d"), "check result contains x.d");
        assertTrue(sub.get("x").asDocument().size() == 4, "check result contains x as document with 4 props");
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("array"), "check result contains array");
        assertTrue(result.asDocument().get("array").isArray(), "check result contains array as array");
        var array = result.asDocument().get("array").asArray();
        assertEquals(5, array.size(), "check result array contains five elements");
        assertEquals(BsonNull.VALUE, array.get(2), "check result 2nd element of array is null");
        assertEquals(BsonNull.VALUE, array.get(3), "check result 3rd element of array is null");
        assertTrue(array.get(1).isString(), "check result second element of array is string");
        assertTrue(array.get(0).isString(), "check result first element of array is string");
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("a"), "check result contains a");

        assertTrue(result.asDocument().containsKey("b"), "check result contains b");
        assertTrue(result.asDocument().get("b").isDocument(), "check result b is document");

        assertTrue(result.asDocument().get("b").asDocument().containsKey("c"), "check result contains b.c");
        assertTrue(result.asDocument().get("b").asDocument().get("c").isArray(), "check result contains b.c as array");
        assertTrue(result.asDocument().get("b").asDocument().get("c").asArray().size() == 3,
                "check result b.c size is 3");
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("array"), "check result contains array");
        assertTrue(result.asDocument().get("array").isArray(), "check result contains array as array");
        var array = result.asDocument().get("array").asArray();
        assertEquals(5, array.size(), "check result array contains five elements");
        assertEquals(BsonNull.VALUE, array.get(2), "check result 2nd element of array is null");
        assertEquals(BsonNull.VALUE, array.get(3), "check result 3rd element of array is null");
        assertTrue(array.get(1).isString(), "check result second element of array is string");
        assertTrue(array.get(0).isString(), "check result first element of array is string");
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("array"), "check result contains array");
        assertTrue(result.asDocument().get("array").isArray(), "check result contains array as array");
        var array = result.asDocument().get("array").asArray();
        assertEquals(5, array.size(), "check result array contains five elements");
        assertEquals(BsonNull.VALUE, array.get(2), "check result 2nd element of array is null");
        assertEquals(BsonNull.VALUE, array.get(3), "check result 3rd element of array is null");
        assertTrue(array.get(1).isDocument(), "check result second element of array is document");
        assertTrue(array.get(0).isDocument(), "check result first element of array is document");
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("sub"), "check result contains sub");
        assertTrue(result.asDocument().get("sub").isArray(), "check result contains sub as array");
        var sub = result.asDocument().get("sub").asArray();
        assertTrue(sub.size() == 4, "check sub contains 4 elements");
        assertTrue(sub.get(0).isDocument(), "check sub first element is a document");
        assertTrue(sub.get(0).asDocument().containsKey("f"), "check sub first element has f property");
        assertTrue(sub.get(0).asDocument().get("f").isString(), "check sub first element has f property as string");
        assertTrue(sub.get(0).asDocument().get("f").asString().getValue().equals("b"),
                "check sub first element has f property is 'b'");
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
        assertTrue(result.isDocument(), "check result is document");
        assertTrue(result.asDocument().containsKey("keys"), "check result contains keys");
        assertTrue(result.asDocument().get("keys").isDocument(), "check result contains keys as document");
        assertTrue(result.asDocument().get("keys").asDocument().containsKey("ids.code"),
                "check result contains keys as document with field 'ids.code'");
    }
}
