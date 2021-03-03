/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.test.integration;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import java.net.URISyntaxException;
import org.apache.http.HttpStatus;
import org.bson.BsonValue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restheart.utils.BsonUtils;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PlainJsonRepIT extends AbstactIT {

    private final String DB = TEST_DB_PREFIX + "-pj-db";
    private final String COLL = "coll";

    HttpResponse<String> resp;

    /**
     *
     * @throws URISyntaxException
     */
    public PlainJsonRepIT() throws URISyntaxException {
    }

    /**
     *
     * @throws Exception
     */
    @Before
    public void createTestData() throws Exception {
        // create test db
        resp = Unirest.put(url(DB))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("create db " + DB,
                HttpStatus.SC_CREATED, resp.getStatus());

        // create collection
        resp = Unirest.put(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("create collection " + DB.concat("/").concat(COLL),
                HttpStatus.SC_CREATED, resp.getStatus());

        Assert.assertEquals("create schema child.json", HttpStatus.SC_CREATED, resp.getStatus());

        // create 10 test docs
        for (int i = 0; i < 10; i++) {
            resp = Unirest.put(url(DB, COLL, "" + i))
                    .basicAuth(ADMIN_ID, ADMIN_PWD)
                    .queryString("wm", "upsert")
                    .header("content-type", "application/json")
                    .body("{'n': " + i + "}")
                    .asString();

            Assert.assertEquals("create doc " + i, HttpStatus.SC_CREATED, resp.getStatus());
        }
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetRoot() throws Exception {
        resp = Unirest.get(HTTP_HOST.toURI())
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNotNull(body.asDocument().get("_returned"));
        Assert.assertTrue(body.asDocument().get("_returned").isNumber());

        Assert.assertNotNull(body.asDocument().get("_size"));
        Assert.assertTrue(body.asDocument().get("_size").isNumber());

        Assert.assertNotNull(body.asDocument().get("_total_pages"));
        Assert.assertTrue(body.asDocument().get("_total_pages").isNumber());

        Assert.assertNotNull(body.asDocument().get("_embedded"));

        Assert.assertTrue(body.asDocument().get("_embedded").isArray());

        Assert.assertTrue(body.asDocument().get("_embedded").asArray().size()
                == body.asDocument().get("_returned").asNumber().intValue());

        body.asDocument().get("_embedded").asArray().forEach(db -> {
            Assert.assertNotNull(db);
            Assert.assertNotNull(db.isDocument());
        });
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetRootNp() throws Exception {
        resp = Unirest.get(HTTP_HOST.toURI())
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .queryString("np", "true")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isArray());

        Assert.assertTrue(body.asArray().size() > 0);

        body.asArray().forEach(db -> {
            Assert.assertNotNull(db);
            Assert.assertNotNull(db.isDocument());
        });
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDb() throws Exception {
        resp = Unirest.get(url(DB))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNotNull(body.asDocument().get("_returned"));
        Assert.assertTrue(body.asDocument().get("_returned").isNumber());

        Assert.assertNotNull(body.asDocument().get("_size"));
        Assert.assertTrue(body.asDocument().get("_size").isNumber());

        Assert.assertNotNull(body.asDocument().get("_total_pages"));
        Assert.assertTrue(body.asDocument().get("_total_pages").isNumber());

        Assert.assertNotNull(body.asDocument().get("_embedded"));

        Assert.assertTrue(body.asDocument().get("_embedded").isArray());

        Assert.assertTrue(body.asDocument().get("_embedded").asArray().size()
                == body.asDocument().get("_returned").asNumber().intValue());

        body.asDocument().get("_embedded").asArray().forEach(coll -> {
            Assert.assertNotNull(coll);
            Assert.assertNotNull(coll.isDocument());
        });
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDbNp() throws Exception {
        resp = Unirest.get(url(DB))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .queryString("np", "true")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isArray());

        Assert.assertTrue(body.asArray().size() > 0);

        body.asArray().forEach(coll -> {
            Assert.assertNotNull(coll);
            Assert.assertNotNull(coll.isDocument());
        });
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetColl() throws Exception {
        resp = Unirest.get(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .queryString("count", "true")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNotNull(body.asDocument().get("_returned"));
        Assert.assertTrue(body.asDocument().get("_returned").isNumber());

        Assert.assertTrue(10
                == body.asDocument().get("_returned").asNumber().intValue());

        Assert.assertNotNull(body.asDocument().get("_size"));
        Assert.assertTrue(body.asDocument().get("_size").isNumber());

        Assert.assertNotNull(body.asDocument().get("_total_pages"));
        Assert.assertTrue(body.asDocument().get("_total_pages").isNumber());

        Assert.assertNotNull(body.asDocument().get("_embedded"));

        Assert.assertTrue(body.asDocument().get("_embedded").isArray());

        Assert.assertTrue(10
                == body.asDocument().get("_embedded").asArray().size());

        body.asDocument().get("_embedded").asArray().forEach(doc -> {
            Assert.assertNotNull(doc);
            Assert.assertNotNull(doc.isDocument());
        });
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetCollEmptyResultSet() throws Exception {
        resp = Unirest.get(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .queryString("page", "2")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNotNull(body.asDocument().get("_returned"));
        Assert.assertTrue(body.asDocument().get("_returned").isNumber());

        Assert.assertTrue(0
                == body.asDocument().get("_returned").asNumber().intValue());

        Assert.assertNotNull(body.asDocument().get("_embedded"));

        Assert.assertTrue(body.asDocument().get("_embedded").isArray());

        Assert.assertTrue(body.asDocument().get("_embedded").asArray().isEmpty());
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetCollNpEmptyResultSet() throws Exception {
        resp = Unirest.get(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .queryString("np", "true")
                .queryString("page", "2")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isArray());

        Assert.assertTrue(body.asArray().isEmpty());

        body.asArray().forEach(doc -> {
            Assert.assertNotNull(doc);
            Assert.assertNotNull(doc.isDocument());
        });
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetIndexes() throws Exception {
        resp = Unirest.get(url(DB, COLL, "_indexes"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNotNull(body.asDocument().get("_returned"));
        Assert.assertTrue(body.asDocument().get("_returned").isNumber());

        Assert.assertNotNull(body.asDocument().get("_size"));
        Assert.assertTrue(body.asDocument().get("_size").isNumber());

        Assert.assertNotNull(body.asDocument().get("_embedded"));

        Assert.assertTrue(body.asDocument().get("_embedded").isArray());

        Assert.assertTrue(body.asDocument().get("_embedded").asArray().size()
                == body.asDocument().get("_returned").asNumber().intValue());

        body.asDocument().get("_embedded").asArray().forEach(index -> {
            Assert.assertNotNull(index);
            Assert.assertNotNull(index.isDocument());
        });
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDoc() throws Exception {
        resp = Unirest.get(url(DB, COLL, "1"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertTrue(body.asDocument().keySet().size() == 3);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDocNp() throws Exception {
        resp = Unirest.get(url(DB, COLL, "1"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .queryString("np", "true")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertTrue(body.asDocument().keySet().size() == 3);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetError() throws Exception {
        resp = Unirest.get(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .queryString("page", "string") // this leads to an error
                .asString();

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNull(body.asDocument().get("_embedded"));

        Assert.assertNotNull(body.asDocument().get("exception"));
        
        Assert.assertTrue(body.asDocument().get("exception").isString());

        Assert.assertNotNull(body.asDocument().get("exception message"));
        
        Assert.assertTrue(body.asDocument().get("exception message").isString());

        Assert.assertNotNull(body.asDocument().get("message"));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDocError() throws Exception {
        resp = Unirest.get(url(DB, COLL, "1"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .queryString("page", "string") // this leads to an error
                .asString();

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNotNull(body.asDocument().get("exception"));
        
        Assert.assertTrue(body.asDocument().get("exception").isString());

        Assert.assertNotNull(body.asDocument().get("exception message"));
        
        Assert.assertTrue(body.asDocument().get("exception message").isString());

        Assert.assertNotNull(body.asDocument().get("message"));
        
        Assert.assertTrue(body.asDocument().get("message").isString());
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetErrorNp() throws Exception {
        resp = Unirest.get(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .queryString("np", "true")
                .queryString("page", "string") // this leads to an error
                .asString();

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNull(body.asDocument().get("_embedded"));

        Assert.assertNotNull(body.asDocument().get("exception"));
        
        Assert.assertTrue(body.asDocument().get("exception").isString());

        Assert.assertNotNull(body.asDocument().get("exception message"));
        
        Assert.assertTrue(body.asDocument().get("exception message").isString());

        // event if np, in case of error return the props
        Assert.assertNotNull(body.asDocument().get("message"));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testAggregation() throws Exception {
        final String _aggrs = "{ \"aggrs\" : [ "
                + "      { "
                + "      \"stages\" : [ "
                + "        { \"$match\" : { \"n\" : { \"$gt\" : 4 } } }"
                + "      ],\n"
                + "      \"type\" : \"pipeline\","
                + "      \"uri\" : \"test\""
                + "      }"
                + "    ]"
                + "}";

        // create aggregation
        resp = Unirest.patch(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body(_aggrs)
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        resp = Unirest.get(url(DB, COLL, "_aggrs", "test"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNotNull(body.asDocument().get("_returned"));

        Assert.assertTrue(body.asDocument().get("_returned").isNumber());

        Assert.assertTrue(body.asDocument().get("_returned").asNumber().intValue() == 5);

        Assert.assertNotNull(body.asDocument().get("_embedded"));

        Assert.assertTrue(body.asDocument().get("_embedded").isArray());

        Assert.assertTrue(body.asDocument().get("_embedded").asArray().size() == 5);

        final String _aggrs2 = "{ \"aggrs\" : [ "
                + "      { "
                + "      \"stages\" : [ "
                + "        { \"$match\" : { \"n\" : { \"$gt\" : 1000 } } }"
                + "      ],\n"
                + "      \"type\" : \"pipeline\","
                + "      \"uri\" : \"test\""
                + "      }"
                + "    ]"
                + "}";

        // update aggregation
        resp = Unirest.patch(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body(_aggrs2)
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        resp = Unirest.get(url(DB, COLL, "_aggrs", "test"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNotNull(body.asDocument().get("_returned"));

        Assert.assertTrue(body.asDocument().get("_returned").isNumber());

        Assert.assertTrue(body.asDocument().get("_returned").asNumber().intValue() == 0);

        Assert.assertNotNull(body.asDocument().get("_embedded"));

        Assert.assertTrue(body.asDocument().get("_embedded").isArray());

        Assert.assertTrue(body.asDocument().get("_embedded").asArray().isEmpty());
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testAggregationEmpty() throws Exception {
        final String _aggrs2 = "{ \"aggrs\" : [ "
                + "      { "
                + "      \"stages\" : [ "
                + "        { \"$match\" : { \"n\" : { \"$gt\" : 1000 } } }"
                + "      ],\n"
                + "      \"type\" : \"pipeline\","
                + "      \"uri\" : \"test\""
                + "      }"
                + "    ]"
                + "}";

        // update aggregation
        resp = Unirest.patch(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body(_aggrs2)
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        resp = Unirest.get(url(DB, COLL, "_aggrs", "test"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNotNull(body.asDocument().get("_returned"));

        Assert.assertTrue(body.asDocument().get("_returned").isNumber());

        Assert.assertTrue(body.asDocument().get("_returned").asNumber().intValue() == 0);

        Assert.assertNotNull(body.asDocument().get("_embedded"));

        Assert.assertTrue(body.asDocument().get("_embedded").isArray());

        Assert.assertTrue(body.asDocument().get("_embedded").asArray().isEmpty());
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testBulkPost() throws Exception {
        resp = Unirest.post(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .header("content-type", "application/json")
                .body("[{'a':1},{'a':2}]")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNotNull(body.asDocument().get("inserted"));

        Assert.assertTrue(body.asDocument().get("inserted").isNumber());

        Assert.assertTrue(body.asDocument().get("inserted")
                .asNumber().intValue() == 2);

        Assert.assertNotNull(body.asDocument().get("_links"));

        Assert.assertTrue(body.asDocument().get("_links").isDocument());

        Assert.assertNotNull(body.asDocument().get("_links")
                .asDocument().get("rh:newdoc"));

        Assert.assertTrue(body.asDocument().get("_links")
                .asDocument().get("rh:newdoc").isArray());

        Assert.assertTrue(body.asDocument().get("_links")
                .asDocument().get("rh:newdoc").asArray().size() == 2);

        Assert.assertTrue(body.asDocument().keySet().size() == 5);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testBulkPatch() throws Exception {
        resp = Unirest.patch(url(DB, COLL, "*"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .queryString("filter", "{'n': {'$gt':5}}")
                .header("content-type", "application/json")
                .body("{'b': 1}]")
                .asString();

        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        Assert.assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        Assert.assertTrue(body.isDocument());

        Assert.assertNotNull(body.asDocument().get("inserted"));

        Assert.assertTrue(body.asDocument().get("inserted").isNumber());

        Assert.assertTrue(body.asDocument().get("inserted").asNumber().intValue() == 0);

        Assert.assertNotNull(body.asDocument().get("matched"));

        Assert.assertTrue(body.asDocument().get("matched").isNumber());

        Assert.assertTrue(body.asDocument().get("matched").asNumber().intValue() == 4);

        Assert.assertNotNull(body.asDocument().get("modified"));

        Assert.assertTrue(body.asDocument().get("modified").isNumber());

        Assert.assertTrue(body.asDocument().get("modified").asNumber().intValue() == 4);

        Assert.assertNull(body.asDocument().get("_links"));
    }
}
