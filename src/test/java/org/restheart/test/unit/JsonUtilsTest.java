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
        
        Assert.assertTrue(checkGetPropsFromPath(json1, "$.*", "{b:1, c: {d:{\"$oid\": \"550c6e62c2e62b5640673e93\"}, e:3}}", "4"));
        
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
        } catch(IllegalArgumentException ex) {
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
        
        for (String exp: expected){
            exps.add(JSON.parse(exp));
        }
        
        System.out.println(json + " | " + path + " -> " + gots + " exprected " + exps);
        
        return exps.equals(gots);
    }
    
    private boolean checkType(Object json, String path, String expectedType) {
        List<Object> gots = JsonUtils.getPropsFromPath(json, path);
        
        boolean typeMatch = true;
        
        for (Object got: gots) {
            typeMatch = typeMatch && JsonUtils.checkType(got, expectedType);
        }
        
        return typeMatch;
    }
}
