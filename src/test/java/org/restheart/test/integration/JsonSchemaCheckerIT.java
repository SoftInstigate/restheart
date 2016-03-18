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
package org.restheart.test.integration;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import java.net.URISyntaxException;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonSchemaCheckerIT extends AbstactIT {
    private final String DB = "test-jsonschema-db";
    private final String COLL_BASIC = "coll_basic";
    private final String COLL_CHILD = "coll_child";
    private final String SCHEMA_STORE = "_schemas";

    public JsonSchemaCheckerIT() throws URISyntaxException {
    }

    @Before
    public void createTestData() throws Exception {
        HttpResponse resp;

        // create test db
        resp = Unirest.put(url(DB))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("create db " + DB, HttpStatus.SC_CREATED, resp.getStatus());

        // create schema store
        resp = Unirest.put(url(DB, SCHEMA_STORE))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("create schema store " + DB.concat("/").concat(SCHEMA_STORE), HttpStatus.SC_CREATED, resp.getStatus());

        // create schemas
        resp = Unirest.put(url(DB, SCHEMA_STORE, "basic"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body(getResourceFile("schemas/basic.json"))
                .asString();

        Assert.assertEquals("create schema basic.json", HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.put(url(DB, SCHEMA_STORE, "parent"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body(getResourceFile("schemas/parent.json"))
                .asString();

        Assert.assertEquals("create schema parent.json", HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.put(url(DB, SCHEMA_STORE, "child"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body(getResourceFile("schemas/child.json"))
                .asString();

        Assert.assertEquals("create schema child.json", HttpStatus.SC_CREATED, resp.getStatus());

        // create test collection basic
        resp = Unirest.put(url(DB, COLL_BASIC))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'checkers': [ { 'name': 'jsonSchema', 'args': { 'schemaId': 'basic' }, 'skipNotSupported': true } ] }")
                .asString();

        Assert.assertEquals("create collection " + DB.concat("/").concat(COLL_BASIC), HttpStatus.SC_CREATED, resp.getStatus());

        // create test child
        resp = Unirest.put(url(DB, COLL_CHILD))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'checkers': [ { 'name': 'jsonSchema', 'args': { 'schemaId': 'child' }, 'skipNotSupported': true } ] }")
                .asString();

        Assert.assertEquals("create collection " + DB.concat("/").concat(COLL_CHILD), HttpStatus.SC_CREATED, resp.getStatus());
    }

    @Test
    public void testPostData() throws Exception {
        HttpResponse resp;
        
        // *** test create invalid data
        
        resp = Unirest.post(url(DB, COLL_BASIC))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'n': 'ciao', 's': 'a' }")
                .asString();
        
        Assert.assertEquals("test invalid data 1", HttpStatus.SC_BAD_REQUEST, resp.getStatus());
        
        resp = Unirest.post(url(DB, COLL_BASIC))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'n': 1, 's': 1 }")
                .asString();
        
        Assert.assertEquals("test invalid data 2", HttpStatus.SC_BAD_REQUEST, resp.getStatus());
        
        resp = Unirest.post(url(DB, COLL_BASIC))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'s': 'string' }")
                .asString();
        
        Assert.assertEquals("test invalid data 3", HttpStatus.SC_BAD_REQUEST, resp.getStatus());

        // *** test create valid data
        
        resp = Unirest.put(url(DB, COLL_BASIC, "doc"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'n': 1, 's': 'string' }")
                .asString();
        
        Assert.assertEquals("test create valid data", HttpStatus.SC_CREATED, resp.getStatus());
        
        // *** test update invalid data
        
        resp = Unirest.patch(url(DB, COLL_BASIC, "doc"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'n': 'string' }")
                .asString();
        
        Assert.assertEquals("test update invalid data", HttpStatus.SC_BAD_REQUEST, resp.getStatus());
        
        // *** test update valid data
        
        resp = Unirest.patch(url(DB, COLL_BASIC, "doc"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'n': 100 }")
                .asString();
        
        Assert.assertEquals("test update valid data", HttpStatus.SC_BAD_REQUEST, resp.getStatus());
    }

    @Test
    @Ignore
    public void testPostDataDotNotation() throws Exception {
        // *** test post valid data with dot notation
    }

    @Test
    @Ignore
    public void testPostIncompleteDataDotNotation() throws Exception {
        // *** test post valid data with dot notation
    }

    @Test
    @Ignore
    public void testPutDataDotNotation() throws Exception {
        // *** test post valid data with dot notation
    }

    @Test
    @Ignore
    public void testPatchData() throws Exception {
        // *** test create valid data
        // *** test patch valid data with dot notation
        // *** test patch invalid key
        // *** test patch invalid key
        // *** test patch wrong type object data
        // *** test patch invalid array data
    }

    /**
     * see bug https://softinstigate.atlassian.net/browse/RH-160
     *
     * @throws Exception
     */
    @Test
    @Ignore
    public void testPatchIncompleteObject() throws Exception {
        // *** test create valid data

        // *** test patch valid data with dot notation
        // an incomplete details object. address and country are nullable but mandatory
    }
}
