/*
 * RESTHeart - the data REST API server
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
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restheart.utils.JsonUtils;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonUtilsTest {

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
    public void testGetPropFromPath() throws Exception {
        String _json1 = "{a: {b:1, c: {d:{\"$oid\": \"550c6e62c2e62b5640673e93\"}, e:3}}, f:4}";
        String _json2 = "{a: [{b:1}, {b:2,c:3}, true]}";
        String _json3 = "{a: [{b:1}, {b:2}, {b:3}]}";
        String _json4 = "{a: [[{b:1}], [{b:2}], [{b:3}]]}";

        Object json1 = JSON.parse(_json1);
        Object json2 = JSON.parse(_json2);
        Object json3 = JSON.parse(_json3);
        Object json4 = JSON.parse(_json4);

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.notexists", "null"));
        Assert.assertTrue(checkType(json1, "$.notexists", "null"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$", _json1));
        Assert.assertTrue(checkType(json1, "$", "object"));
        Assert.assertFalse(checkType(json1, "$", "number"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.*", "{a: {b:1, c: {d:{\"$oid\": \"550c6e62c2e62b5640673e93\"}, e:3}}}", "{f:4}"));
        Assert.assertTrue(checkGetPropsFromPath(json1, "$.*.*", "{b:1}", "{c: {d:{\"$oid\": \"550c6e62c2e62b5640673e93\"}, e:3}}"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a", "{b:1, c: {d:{\"$oid\": \"550c6e62c2e62b5640673e93\"},e:3}}, f:4}"));
        Assert.assertTrue(checkType(json1, "$.a", "object"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a.b", "1"));
        Assert.assertTrue(checkType(json1, "$.a.b", "number"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a.c", "{d:{\"$oid\": \"550c6e62c2e62b5640673e93\"},e:3}"));
        Assert.assertTrue(checkType(json1, "$.a.c", "object"));

        Assert.assertTrue(checkGetPropsFromPath(json1, "$.a.c.d", "{\"$oid\": \"550c6e62c2e62b5640673e93\"}"));
        Assert.assertTrue(checkType(json1, "$.a.c.d", "objectid"));

        Assert.assertTrue(checkGetPropsFromPath(json2, "$.a", "[{b:1}, {b:2,c:3}, true]"));
        Assert.assertTrue(checkType(json2, "$.a", "array"));

        Assert.assertTrue(checkGetPropsFromPath(json2, "$.a.[*]", "{b:1}", "{b:2,c:3}", "true"));
        Assert.assertFalse(checkType(json2, "$.a.[*]", "object"));

        Assert.assertTrue(checkGetPropsFromPath(json2, "$.a.[*].c", "null", "3", "null"));

        try {
            checkGetPropsFromPath(json2, "a.[*].c", "exception, third element of array is not an object");
        } catch (IllegalArgumentException ex) {
            Assert.assertNotNull(ex);
        }

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

    private boolean checkGetPropsFromPath(Object json, String path, String... expected) {
        List<Object> gots = JsonUtils.getPropsFromPath(json, path);

        List<Object> exps = new ArrayList<>();

        for (String exp : expected) {
            exps.add(JSON.parse(exp));
        }

        System.out.println(json + " | " + path + " -> " + gots + " exprected " + exps);

        return exps.equals(gots);
    }

    private boolean checkType(Object json, String path, String expectedType) {
        List<Object> gots = JsonUtils.getPropsFromPath(json, path);

        boolean typeMatch = true;

        for (Object got : gots) {
            typeMatch = typeMatch && JsonUtils.checkType(got, expectedType);
        }

        return typeMatch;
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

        //System.out.println("$.*" + " -> "+ JsonUtils.getPropsFromPath(json, "$.*"));
        //System.out.println("$._id" + " -> "+ JsonUtils.getPropsFromPath(json, "$._id"));
        //System.out.println("$.descr" + " -> "+ JsonUtils.getPropsFromPath(json, "$.descr"));
        //System.out.println("$.items" + " -> "+ JsonUtils.getPropsFromPath(json, "$.items"));
        //System.out.println("$.items.*" + " -> "+ JsonUtils.getPropsFromPath(json, "$.items.*"));
        //System.out.println("$.items.*.*" + " -> " + JsonUtils.getPropsFromPath(json, "$.items.*.*"));
        //System.out.println("$.items.*.descr" + " -> "+ JsonUtils.getPropsFromPath(json, "$.items.*.descr"));
        //System.out.println("$.items.*.items" + " -> "+ JsonUtils.getPropsFromPath(json, "$.items.*.items"));
        //System.out.println("$.items.*.items.*" + " -> "+ JsonUtils.getPropsFromPath(json, "$.items.*.items.*"));
        //System.out.println("$.items.*.items.*.*" + " -> "+ JsonUtils.getPropsFromPath(json, "$.items.*.items.*.*"));
        //System.out.println("$.items.*.items.*.descr" + " -> "+ JsonUtils.getPropsFromPath(json, "$.items.*.items.*.descr"));
        //System.out.println("$.items.*.items.*.values" + " -> "+ JsonUtils.getPropsFromPath(json, "$.items.*.items.*.values"));
        //System.out.println("$.items.*.items.*.values.*" + " -> "+ JsonUtils.getPropsFromPath(json, "$.items.*.items.*.values.*"));
        //System.out.println("$.items.*.items.*.values.*.descr" + " -> "+ JsonUtils.getPropsFromPath(json, "$.items.*.items.*.values.*.descr"));
        //System.out.println("$.items.*.items.*.values.*.svalue" + " -> "+ JsonUtils.getPropsFromPath(json, "$.items.*.items.*.values.*.svalue"));

        String path = "$.items.*.*";
        
        Assert.assertTrue(JsonUtils.countPropsFromPath(json, path) == 2);
        Assert.assertTrue(JsonUtils.countPropsFromPath(json, path, false) == 4);
    }
}
