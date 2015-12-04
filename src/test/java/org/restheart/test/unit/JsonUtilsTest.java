/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.test.unit;

import com.mongodb.util.JSON;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonUtilsTest {
    private static final Logger LOG = LoggerFactory.getLogger(JsonUtilsTest.class);
    
    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    public JsonUtilsTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }
    
    @Test
    public void testMinify() {
        
        
        String json = "{ '_id'  :   {   '$in' : [1, 20.0, 'id']}}";
        String minified = "{'_id':{'$in':[1,20.0,'id']}}";
        
        Assert.assertEquals(minified, JsonUtils.minify(json));
    }

    @Test
    public void testGetPropFromPath() throws Exception {
        String _json1 = "{a: {b:1, c: {d:{\"$oid\": \"550c6e62c2e62b5640673e93\"}, e:3}}, f: null}";
        String _json2 = "{a: [{b:1}, {b:2,c:3}, {d:4, c:null}, true]}";
        String _json3 = "{a: [{b:1}, {b:2}, {b:3}]}";
        String _json4 = "{a: [[{b:1}], [{b:2}], [{b:3}]]}";
        String _json5 = "{a: []}";
        String _json6 = "{a1: [{f1:1, f2:2, a2:[{f1:1,f2:2}]}, {f1:1, f2:2}]}";

        Object json1 = JSON.parse(_json1);
        Object json2 = JSON.parse(_json2);
        Object json3 = JSON.parse(_json3);
        Object json4 = JSON.parse(_json4);
        Object json5 = JSON.parse(_json5);
        Object json6 = JSON.parse(_json6);

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

    @Test
    public void testJsonArray() throws Exception {
        String _json1 = "{a: []}}";
        String _json2 = "{a: [{}]}}";
        String _json3 = "{a: [{f: 1}]}}";
        String _json4 = "{a: [{e: 1}, {e: 2}, {e: 3}]}}";
        String _json5 = "{a: {1: {e: 1}, 2: {e: 2}, 3: {e: 3}}}}";

        Object json1 = JSON.parse(_json1);
        Object json2 = JSON.parse(_json2);
        Object json3 = JSON.parse(_json3);
        Object json4 = JSON.parse(_json4);
        Object json5 = JSON.parse(_json5);

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
    
    @Test
    public void testJsonObject() throws Exception {
        String _json1 = "{o: {}}";
        String _json2 = "{o: {o: {}}}";
        String _json3 = "{o: {o: {f: 1}}}";

        Object json1 = JSON.parse(_json1);
        Object json2 = JSON.parse(_json2);
        Object json3 = JSON.parse(_json3);

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

        Object json = JSON.parse(_json);

        LOG.debug("$.*" + " -> " + JsonUtils.getPropsFromPath(json, "$.*"));
        LOG.debug("$._id" + " -> " + JsonUtils.getPropsFromPath(json, "$._id"));
        LOG.debug("$.descr" + " -> " + JsonUtils.getPropsFromPath(json, "$.descr"));
        LOG.debug("$.items" + " -> " + JsonUtils.getPropsFromPath(json, "$.items"));
        LOG.debug("$.items.*" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*"));
        LOG.debug("$.items.*.*" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*.*"));
        LOG.debug("$.items.*.descr" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*.descr"));
        LOG.debug("$.items.*.items" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*.items"));
        LOG.debug("$.items.*.items.*" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*.items.*"));
        LOG.debug("$.items.*.items.*.*" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*.items.*.*"));
        LOG.debug("$.items.*.items.*.descr" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*.items.*.descr"));
        LOG.debug("$.items.*.items.*.values" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*.items.*.values"));
        LOG.debug("$.items.*.items.*.values.*" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*.items.*.values.*"));
        LOG.debug("$.items.*.items.*.values.*.descr" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*.items.*.values.*.descr"));
        LOG.debug("$.items.*.items.*.values.*.svalue" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*.items.*.values.*.svalue"));

        String path = "$.items.*.*";

        try {
            Assert.assertTrue(JsonUtils.countPropsFromPath(json, path) == 4);
        } catch (IllegalArgumentException ex) {
            Assert.fail(ex.toString());
        }
    }

    private boolean eq(List<Optional<Object>> left, List<Optional<Object>> right) {
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
            Optional<Object> lo = left.get(cont);
            Optional<Object> ro = right.get(cont);

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

    private boolean checkGetPropsFromPath(Object json, String path, String... expected) {
        List<Optional<Object>> gots;

        try {
            gots = JsonUtils.getPropsFromPath(json, path);
        } catch (IllegalArgumentException ex) {
            Assert.fail(ex.toString());
            return false;
        }

        if (expected == null) {
            LOG.debug(json + " | " + path + " -> " + gots + " exprected null result (missing field)");
            return gots == null;
        }

        List<Optional<Object>> exps = new ArrayList<>();

        for (String exp : expected) {
            if (exp == null) {
                exps.add(null);
            } else {
                exps.add(Optional.ofNullable(JSON.parse(exp)));
            }
        }

        LOG.debug(json + " | " + path + " -> " + gots + " exprected " + Arrays.toString(expected));

        return eq(exps, gots);
    }

    private boolean checkType(Object json, String path, String expectedType) {
        List<Optional<Object>> gots;
        try {
            gots = JsonUtils.getPropsFromPath(json, path);
        } catch (IllegalArgumentException ex) {
            Assert.fail(ex.toString());
            return false;
        }

        // null means that the path does not exist
        if (gots == null) {
            return false;
        }

        boolean typeMatch = true;

        for (Optional<Object> got : gots) {
            typeMatch = typeMatch && JsonUtils.checkType(got, expectedType);
        }

        return typeMatch;
    }
}
