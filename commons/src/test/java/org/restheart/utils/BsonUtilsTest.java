/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
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
public class BsonUtilsTest {

    private static final Logger LOG = LoggerFactory.getLogger(BsonUtilsTest.class);

    /**
     *
     */
    @BeforeClass
    public static void setUpClass() {
    }

    /**
     *
     */
    @AfterClass
    public static void tearDownClass() {
    }

    /**
     *
     */
    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    /**
     *
     */
    public BsonUtilsTest() {
    }

    /**
     *
     */
    @Before
    public void setUp() {
    }

    /**
     *
     */
    @After
    public void tearDown() {
    }

    /**
     *
     */
    @Test
    public void testMinify() {
        String json = "{ '_id'  :   {   '$in' : [1, 20.0, 'id']}}";
        String minified = "{'_id':{'$in':[1,20.0,'id']}}";

        Assert.assertEquals(minified, BsonUtils.minify(json));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetPropFromPath() throws Exception {
        String _json1 = "{a: {b:1, c: {d:{\"$oid\": \"550c6e62c2e62b5640673e93\"}, e:3}}, f: null}";
        String _json2 = "{a: [{b:1}, {b:2,c:3}, {d:4, c:null}, true]}";
        String _json3 = "{a: [{b:1}, {b:2}, {b:3}]}";
        String _json4 = "{a: [[{b:1}], [{b:2}], [{b:3}]]}";
        String _json5 = "{a: []}";
        String _json6 = "{a1: [{f1:1, f2:2, a2:[{f1:1,f2:2}]}, {f1:1, f2:2}]}";

        BsonDocument json1 = BsonDocument.parse(_json1);
        BsonDocument json2 = BsonDocument.parse(_json2);
        BsonDocument json3 = BsonDocument.parse(_json3);
        BsonDocument json4 = BsonDocument.parse(_json4);
        BsonDocument json5 = BsonDocument.parse(_json5);
        BsonDocument json6 = BsonDocument.parse(_json6);

        Assert.assertTrue(checkGetPropsFromPath(json6, "$.a1.[*].a2", "[{f1:1,f2:2}]", null));
        Assert.assertTrue(checkGetPropsFromPath(json6, "$.a1.[*].a2.[*].f1", "1"));

        Assert.assertTrue(checkGetPropsFromPath(json5, "$.a", "[]"));
        Assert.assertTrue(checkGetPropsFromPath(json5, "$.a.[*]"));
        Assert.assertTrue(checkGetPropsFromPath(json5, "$.a.[*].*"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.notexists", (String[]) null));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.f", "null"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$", _json1));
        Assert.assertTrue(checkType(json1, "$", "object"));
        Assert.assertFalse(checkType(json1, "$", "number"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a", "{b:1, c: {d:{\"$oid\": \"550c6e62c2e62b5640673e93\"}, e:3}}"));
        Assert.assertTrue(checkGetPropsFromPath(json1, "$.f", "null"));
        Assert.assertTrue(checkGetPropsFromPath(json1, "$.*", "{b:1, c: {d:{\"$oid\": \"550c6e62c2e62b5640673e93\"}, e:3}}", "null"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a.b", "1"));
        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a.c", "{d:{\"$oid\": \"550c6e62c2e62b5640673e93\"}, e:3}"));
        Assert.assertTrue(checkGetPropsFromPath(json1, "$.*.*", "1", "{d:{\"$oid\": \"550c6e62c2e62b5640673e93\"}, e:3}", null));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a", "{b:1, c: {d:{\"$oid\": \"550c6e62c2e62b5640673e93\"},e:3}}, f: null}"));
        Assert.assertTrue(checkType(json1, "$.a", "object"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a.b", "1"));
        Assert.assertTrue(checkType(json1, "$.a.b", "number"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a.c", "{d:{\"$oid\": \"550c6e62c2e62b5640673e93\"},e:3}"));
        Assert.assertTrue(checkType(json1, "$.a.c", "object"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a.c.d", "{\"$oid\": \"550c6e62c2e62b5640673e93\"}"));
        Assert.assertTrue(checkType(json1, "$.a.c.d", "objectid"));

        Assert.assertTrue(checkGetPropsFromPath(json2, "$.a", "[{b:1}, {b:2,c:3}, {d:4, c: null}, true]"));
        Assert.assertTrue(checkType(json2, "$.a", "array"));

        Assert.assertTrue(checkGetPropsFromPath(json2, "$.a.[*]", "{b:1}", "{b:2,c:3}", "{d:4, c: null}", "true"));
        Assert.assertFalse(checkType(json2, "$.a.[*]", "object"));

        Assert.assertTrue(checkGetPropsFromPath(json2, "$.a.[*].c", null, "3", "null", null));

        Assert.assertTrue(checkGetPropsFromPath(json2, "$.a.[*].c.*"));

        Assert.assertTrue(checkGetPropsFromPath(json3, "$.a", "[{b:1}, {b:2}, {b:3}]"));
        Assert.assertTrue(checkType(json3, "$.a", "array"));

        Assert.assertTrue(checkGetPropsFromPath(json3, "$.a.[*]", "{b:1}", "{b:2}", "{b:3}"));
        Assert.assertTrue(checkType(json3, "$.a.[*]", "object"));

        Assert.assertTrue(checkGetPropsFromPath(json3, "$.a.[*].b", "1", "2", "3"));
        Assert.assertTrue(checkType(json3, "$.a.[*].b", "number"));

        Assert.assertTrue(checkGetPropsFromPath(json4, "$", "{a: [[{b:1}], [{b:2}], [{b:3}]]}"));
        Assert.assertTrue(checkType(json4, "$", "object"));

        Assert.assertTrue(checkGetPropsFromPath(json4, "$.a", "[[{b:1}], [{b:2}], [{b:3}]]"));
        Assert.assertTrue(checkType(json4, "$.a", "array"));

        Assert.assertTrue(checkGetPropsFromPath(json4, "$.a.[*]", "[{b:1}]", "[{b:2}]", "[{b:3}]"));
        Assert.assertTrue(checkType(json4, "$.a.[*]", "array"));

        Assert.assertTrue(checkGetPropsFromPath(json4, "$.a.[*].[*].b", "1", "2", "3"));
        Assert.assertTrue(checkType(json4, "$.a.[*].[*].b", "number"));

    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testJsonArray() throws Exception {
        String _json1 = "{a: []}}";
        String _json2 = "{a: [{}]}}";
        String _json3 = "{a: [{f: 1}]}}";
        String _json4 = "{a: [{e: 1}, {e: 2}, {e: 3}]}}";
        String _json5 = "{a: {'1': {e: 1}, '2': {e: 2}, '3': {e: 3}}}}";

        BsonDocument json1 = BsonDocument.parse(_json1);
        BsonDocument json2 = BsonDocument.parse(_json2);
        BsonDocument json3 = BsonDocument.parse(_json3);
        BsonDocument json4 = BsonDocument.parse(_json4);
        BsonDocument json5 = BsonDocument.parse(_json5);

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a", "[]"));
        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a.[*]"));
        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a.[*].f"));

        Assert.assertTrue(checkGetPropsFromPath(json2, "$.a", "[{}]"));
        Assert.assertTrue(checkGetPropsFromPath(json2, "$.a.[*]", "{}"));
        Assert.assertTrue(checkGetPropsFromPath(json2, "$.a.[*].f", (String) null));

        Assert.assertTrue(checkGetPropsFromPath(json3, "$.a", "[{f: 1}]"));
        Assert.assertTrue(checkGetPropsFromPath(json3, "$.a.[*]", "{f: 1}"));
        Assert.assertTrue(checkGetPropsFromPath(json3, "$.a.[*].f", "1"));

        Assert.assertTrue(checkGetPropsFromPath(json4, "$.a.[*].e", "1", "2", "3"));

        Assert.assertTrue(checkGetPropsFromPath(json4, "$.a.[*].e", "1", "2", "3"));

        Assert.assertTrue(checkGetPropsFromPath(json5, "$.a.*", "{e: 1}", "{e: 2}", "{e: 3}"));

        // justification of the following: even if "a! is an object, it has all numeric values
        // on mongodb you can use the dot notation on arrays and do the following on RESTHeart
        // PATCH /db/coll/doc {"a.1", {"e": 1000}}
        // the content turns internally to {"a": {"1": {"e":1000}}}
        Assert.assertTrue(checkGetPropsFromPath(json5, "$.a.[*]", "{e: 1}", "{e: 2}", "{e: 3}"));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testJsonObject() throws Exception {
        String _json1 = "{o: {}}";
        String _json2 = "{o: {o: {}}}";
        String _json3 = "{o: {o: {f: 1}}}";

        BsonDocument json1 = BsonDocument.parse(_json1);
        BsonDocument json2 = BsonDocument.parse(_json2);
        BsonDocument json3 = BsonDocument.parse(_json3);

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.o", "{}"));
        Assert.assertTrue(checkGetPropsFromPath(json1, "$.*", "{}"));
        Assert.assertTrue(checkGetPropsFromPath(json1, "$.o.*"));
        Assert.assertTrue(checkGetPropsFromPath(json1, "$.o.*.f"));

        Assert.assertTrue(checkGetPropsFromPath(json2, "$.o", "{o: {}}"));
        Assert.assertTrue(checkGetPropsFromPath(json2, "$.*", "{o: {}}"));
        Assert.assertTrue(checkGetPropsFromPath(json2, "$.o.o", "{}"));
        Assert.assertTrue(checkGetPropsFromPath(json2, "$.o.*", "{}"));
        Assert.assertTrue(checkGetPropsFromPath(json2, "$.o.*.f", (String) null));

        Assert.assertTrue(checkGetPropsFromPath(json3, "$.o", "{o: {f: 1}}"));
        Assert.assertTrue(checkGetPropsFromPath(json3, "$.*", "{o: {f: 1}}"));
        Assert.assertTrue(checkGetPropsFromPath(json3, "$.o.o", "{f: 1}"));
        Assert.assertTrue(checkGetPropsFromPath(json3, "$.o.*", "{f: 1}"));
        Assert.assertTrue(checkGetPropsFromPath(json3, "$.o.*.f", "1"));
    }

    /**
     *
     */
    @Test
    public void checkCountOnComplexJson() {
        String _json = "{\n"
                + "    \"_id\": \"project-processes\",\n"
                + "    \"descr\": \"Progetto - Processi\",\n"
                + "    \"items\": {\n"
                + "        \"manufactoring\": {\n"
                + "            \"descr\": \"Lavorazioni e Costruzioni\",\n"
                + "            \"items\": {\n"
                + "                \"strobel\": {\n"
                + "                    \"descr\": \"Strobel\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"double_lasting\": {\n"
                + "                    \"descr\": \"Sacchetto o Double Lasting\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 2\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"mounted\": {\n"
                + "                    \"descr\": \"Montato\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"membrane_on_upper\": {\n"
                + "                    \"descr\": \"Membrana su Tomaia\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"bootie\": {\n"
                + "                    \"descr\": \"Bootie\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"tubolar\": {\n"
                + "                    \"descr\": \"Tubolare\",\n"
                + "                    \"type\": \"boolean\",\n"
                + "                    \"svalues\": [\n"
                + "                        0,\n"
                + "                        1.5\n"
                + "                    ]\n"
                + "                },\n"
                + "                \"others\": {\n"
                + "                    \"descr\": \"Altro\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"injection\": {\n"
                + "                    \"descr\": \"Iniezione\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"injection_casting\": {\n"
                + "                    \"descr\": \"Iniezione per colata\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"glue\": {\n"
                + "                    \"descr\": \"Incollata\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"blake\": {\n"
                + "                    \"descr\": \"Blake\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"california\": {\n"
                + "                    \"descr\": \"California\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"goodyear\": {\n"
                + "                    \"descr\": \"Goodyear\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"ideal\": {\n"
                + "                    \"descr\": \"Ideal\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"opanks\": {\n"
                + "                    \"descr\": \"Opanks\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"vulcanized\": {\n"
                + "                    \"descr\": \"Vulcanizzata\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"best_process\": {\n"
                + "                    \"descr\": \"Best process\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 3\n"
                + "                        }\n"
                + "                    }\n"
                + "                }\n"
                + "            }\n"
                + "        },\n"
                + "        \"treatments\": {\n"
                + "            \"descr\": \"Trattamenti\",\n"
                + "            \"items\": {\n"
                + "                \"dye\": {\n"
                + "                    \"descr\": \"Tinta in capo/verniciatura\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"stonewash\": {\n"
                + "                    \"descr\": \"Stone Wash\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 1.75\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"colours_faded\": {\n"
                + "                    \"descr\": \"Slavati\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 2.5\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"creams_waxes\": {\n"
                + "                    \"descr\": \"Cere e Creme\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 3.25\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"spray\": {\n"
                + "                    \"descr\": \"Spray\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 3.25\n"
                + "                        }\n"
                + "                    }\n"
                + "                },\n"
                + "                \"none\": {\n"
                + "                    \"descr\": \"Nessun Trattamento\",\n"
                + "                    \"type\": \"boolean\",\n"
                + "                    \"values\": {\n"
                + "                        \"no\": {\n"
                + "                            \"descr\": \"Si\",\n"
                + "                            \"svalue\": 0\n"
                + "                        },\n"
                + "                        \"yes\": {\n"
                + "                            \"descr\": \"No\",\n"
                + "                            \"svalue\": 4\n"
                + "                        }\n"
                + "                    }\n"
                + "                }\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "}";

        BsonDocument json = BsonDocument.parse(_json);

        LOG.debug("$.*" + " -> " + BsonUtils.getPropsFromPath(json, "$.*"));
        LOG.debug("$._id" + " -> " + BsonUtils.getPropsFromPath(json, "$._id"));
        LOG.debug("$.descr" + " -> " + BsonUtils.getPropsFromPath(json, "$.descr"));
        LOG.debug("$.items" + " -> " + BsonUtils.getPropsFromPath(json, "$.items"));
        LOG.debug("$.items.*" + " -> " + BsonUtils.getPropsFromPath(json, "$.items.*"));
        LOG.debug("$.items.*.*" + " -> " + BsonUtils.getPropsFromPath(json, "$.items.*.*"));
        LOG.debug("$.items.*.descr" + " -> " + BsonUtils.getPropsFromPath(json, "$.items.*.descr"));
        LOG.debug("$.items.*.items" + " -> " + BsonUtils.getPropsFromPath(json, "$.items.*.items"));
        LOG.debug("$.items.*.items.*" + " -> " + BsonUtils.getPropsFromPath(json, "$.items.*.items.*"));
        LOG.debug("$.items.*.items.*.*" + " -> " + BsonUtils.getPropsFromPath(json, "$.items.*.items.*.*"));
        LOG.debug("$.items.*.items.*.descr" + " -> " + BsonUtils.getPropsFromPath(json, "$.items.*.items.*.descr"));
        LOG.debug("$.items.*.items.*.values" + " -> " + BsonUtils.getPropsFromPath(json, "$.items.*.items.*.values"));
        LOG.debug("$.items.*.items.*.values.*" + " -> " + BsonUtils.getPropsFromPath(json, "$.items.*.items.*.values.*"));
        LOG.debug("$.items.*.items.*.values.*.descr" + " -> " + BsonUtils.getPropsFromPath(json, "$.items.*.items.*.values.*.descr"));
        LOG.debug("$.items.*.items.*.values.*.svalue" + " -> " + BsonUtils.getPropsFromPath(json, "$.items.*.items.*.values.*.svalue"));

        String path = "$.items.*.*";

        try {
            Assert.assertTrue(BsonUtils.countPropsFromPath(json, path) == 4);
        } catch (IllegalArgumentException ex) {
            Assert.fail(ex.toString());
        }
    }

    /**
     *
     */
    @Test
    public void testParseToBsonObject() {
        String object = BsonUtils.minify("{\"a\" :1 }");

        BsonValue bson = BsonUtils.parse(object);

        String actual = BsonUtils.toJson(bson);

        Assert.assertEquals(object, actual);
    }

    /**
     *
     */
    @Test
    public void testParseToBsonArray() {
        String array = "[\"a\", \"b\", 2 ]";

        BsonValue bson = BsonUtils.parse(array);

        String actual = BsonUtils.toJson(bson);

        Assert.assertEquals(BsonUtils.minify(array), actual);
    }

    /**
     *
     */
    @Test
    public void testParseObjectId() {
        ObjectId id = new ObjectId();

        BsonValue parsed = BsonUtils.parse(
                "{'$oid':'"
                        .concat(id.toString())
                        .concat("'}"));

        Assert.assertTrue(parsed.isObjectId());
        Assert.assertEquals(parsed.asObjectId().getValue(), id);
    }

    /**
     *
     */
    @Test
    public void testParseFloat() {
        BsonValue parsed = BsonUtils.parse("3.1415");

        Assert.assertTrue(parsed.isNumber());
        Assert.assertEquals(parsed.asDouble(), new BsonDouble(3.1415));
    }

    /**
     *
     */
    @Test
    public void testParseString() {
        BsonValue parsed = BsonUtils.parse("'hello'");

        Assert.assertTrue(parsed.isString());
        Assert.assertEquals(parsed.asString(), new BsonString("hello"));
    }

    /**
     *
     */
    @Test
    public void testParseEmptyString() {
        BsonValue parsed = BsonUtils.parse("''");

        Assert.assertTrue(parsed.isString());
        Assert.assertEquals(parsed.asString(), new BsonString(""));
    }

    /**
     *
     */
    @Test
    public void testParseToBsonArrayOfObjectets() {
        String arrayOfObjs = "[{\"a\" :1 },{\"b\" :2 }]";

        BsonValue bson = BsonUtils.parse(arrayOfObjs);

        String actual = BsonUtils.toJson(bson);

        Assert.assertEquals(BsonUtils.minify(arrayOfObjs), actual);
    }

    private boolean eq(List<Optional<BsonValue>> left, List<Optional<BsonValue>> right) {
        if (left == null && right != null) {
            return false;
        }

        if (left != null && right == null) {
            return false;
        }

        if (left == null && right == null) {
            return true;
        }

        if (left.size() != right.size()) {
            return false;
        }

        boolean ret = true;

        for (int cont = 0; cont < left.size(); cont++) {
            Optional<BsonValue> lo = left.get(cont);
            Optional<BsonValue> ro = right.get(cont);

            if (lo == null && ro != null) {
                ret = false;
                break;
            }

            if (lo != null && ro == null) {
                ret = false;
                break;
            }

            if (lo != null && ro != null) {
                if (lo.isPresent() && !ro.isPresent()) {
                    ret = false;
                    break;
                }

                if (!lo.isPresent() && ro.isPresent()) {
                    ret = false;
                    break;
                }

                if (lo.isPresent() && ro.isPresent() && !lo.get().equals(ro.get())) {
                    ret = false;
                    break;
                }
            }
        }

        return ret;
    }

    private boolean checkGetPropsFromPath(BsonValue json, String path, String... expected) {
        List<Optional<BsonValue>> gots;

        try {
            gots = BsonUtils.getPropsFromPath(json, path);
        } catch (IllegalArgumentException ex) {
            Assert.fail(ex.toString());
            return false;
        }

        if (expected == null) {
            LOG.debug(json + " | " + path + " -> " + gots + " exprected null result (missing field)");
            return gots == null;
        }

        List<Optional<BsonValue>> exps = new ArrayList<>();

        for (String exp : expected) {
            if (exp == null) {
                exps.add(null);
            } else {
                BsonValue _exp = BsonUtils.parse(exp);

                if (_exp.isNull()) {
                    exps.add(Optional.empty());
                } else {
                    exps.add(Optional.ofNullable(_exp));
                }
            }
        }

        LOG.debug(json + " | " + path + " -> " + gots + " exprected " + Arrays.toString(expected));

        return eq(exps, gots);
    }

    private boolean checkType(BsonDocument json, String path, String expectedType) {
        List<Optional<BsonValue>> gots;
        try {
            gots = BsonUtils.getPropsFromPath(json, path);
        } catch (IllegalArgumentException ex) {
            Assert.fail(ex.toString());
            return false;
        }

        // null means that the path does not exist
        if (gots == null) {
            return false;
        }

        boolean typeMatch = true;

        for (Optional<BsonValue> got : gots) {
            typeMatch = typeMatch && BsonUtils.checkType(got, expectedType);
        }

        return typeMatch;
    }

    /**
     *
     */
    @Test
    public void testJsonUnflatten() {
        BsonDocument grandchild1 = new BsonDocument("a", BsonNull.VALUE);
        grandchild1.put("b", BsonNull.VALUE);

        BsonDocument grandchild2 = new BsonDocument("a", BsonNull.VALUE);

        BsonDocument child1 = new BsonDocument("grandchild1", grandchild1);
        BsonDocument child2 = new BsonDocument("grandchild2", grandchild2);

        BsonDocument root = new BsonDocument("child1", child1);

        root.put("child2", child2);

        BsonDocument flatten = BsonUtils.flatten(root, false).asDocument();

        Assert.assertTrue(flatten.size() == 3);
        Assert.assertTrue(flatten.containsKey("child1.grandchild1.a"));
        Assert.assertTrue(flatten.containsKey("child1.grandchild1.b"));
        Assert.assertTrue(flatten.containsKey("child2.grandchild2.a"));

        BsonValue unflatten = BsonUtils.unflatten(flatten);

        Assert.assertTrue(unflatten.isDocument());
        Assert.assertTrue(unflatten.asDocument().containsKey("child1"));
        Assert.assertTrue(unflatten.asDocument().containsKey("child2"));

        Assert.assertTrue(unflatten.asDocument().get("child1").isDocument());
        Assert.assertTrue(unflatten.asDocument().get("child2").isDocument());
        Assert.assertTrue(unflatten.asDocument().get("child1")
                .asDocument().containsKey("grandchild1"));
        Assert.assertTrue(unflatten.asDocument().get("child2")
                .asDocument().containsKey("grandchild2"));
    }

    /**
     *
     */
    @Test
    public void testParseLong() {
        var json = "[{'n':2084824280},{'n':5887391606}]";

        var parsed = BsonUtils.parse(json);

        System.out.println(BsonUtils.toJson(parsed));

        long l = 1111111115887391606l;

        var json2 = "[{'n':2084824280},{'n':"+l+"}]";

        var parsed2 = BsonUtils.parse(json2);

        System.out.println(BsonUtils.toJson(parsed2));
        System.out.println(parsed2);

        var json3 = "[{'n':2084824280},{'d':{'$date':"+ System.currentTimeMillis()+"}}]";

        var parsed3 = BsonUtils.parse(json3);

        System.out.println(BsonUtils.toJson(parsed3));
        System.out.println(parsed3);
    }

    /**
     *
     */
    @Test
    public void testParseLong2() {
        System.out.println(BsonUtils.toJson(
                BsonUtils.parse(
                        "{'n':"+4294967296l+"}")));

        var ls = BsonUtils.toJson(
                BsonUtils.parse(
                        "{'n':"+(5999999999l)+"}"));

        System.out.println(ls);

        System.out.println(BsonUtils.toJson(
                BsonUtils.parse(ls)));
    }

    /**
     *
     */
    @Test
    public void testParseInt() {
        System.out.println(BsonUtils.toJson(
                BsonUtils.parse(
                        "{'n':"+10+"}")));
    }

    /**
     *
     */
    @Test
    public void testParseDouble() {
        System.out.println(BsonUtils.toJson(
                BsonUtils.parse(
                        "{'n':{'$numberDouble':'11111111158873916063432424232349289023842309842039587209357329578573489573958734985753498573495743957349839'}}")));
    }

    @Test
    public void testFirstNonWhitespace() {
        var s1 = "{ 'a': 1, 'b': 2, 'c': 3 }";
        Assert.assertEquals('{', BsonUtils.firstNonWhitespace(s1));

        var s2 = "      { 'a': 1, 'b': 2, 'c': 3 }";
        Assert.assertEquals('{', BsonUtils.firstNonWhitespace(s2));

        var s3 = "[ 1,2,3 ]";
        Assert.assertEquals('[', BsonUtils.firstNonWhitespace(s3));

        var s4 = "      [ 1,2,3 ]";
        Assert.assertEquals('[', BsonUtils.firstNonWhitespace(s4));

        var empty = "";
        Assert.assertEquals(Character.MIN_VALUE, BsonUtils.firstNonWhitespace(empty));

        var empty2 = "      ";
        Assert.assertEquals(Character.MIN_VALUE, BsonUtils.firstNonWhitespace(empty2));
    }
}
