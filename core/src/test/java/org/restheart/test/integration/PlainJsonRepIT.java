/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;

import org.apache.http.HttpStatus;
import org.bson.BsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restheart.utils.BsonUtils;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;

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
    @BeforeEach
    public void createTestData() throws Exception {
        // create test db
        resp = Unirest.put(url(DB))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(), "create db " + DB);

        // create collection
        resp = Unirest.put(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(), "create collection " + DB.concat("/").concat(COLL));

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(), "create schema child.json");

        // create 10 test docs
        for (int i = 0; i < 10; i++) {
            resp = Unirest.put(url(DB, COLL, "" + i))
                    .basicAuth(ADMIN_ID, ADMIN_PWD)
                    .queryString("wm", "upsert")
                    .header("content-type", "application/json")
                    .body("{'n': " + i + "}")
                    .asString();

            assertEquals(HttpStatus.SC_CREATED, resp.getStatus(), "create doc " + i);
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNotNull(body.asDocument().get("_returned"));
        assertTrue(body.asDocument().get("_returned").isNumber());

        assertNotNull(body.asDocument().get("_size"));
        assertTrue(body.asDocument().get("_size").isNumber());

        assertNotNull(body.asDocument().get("_total_pages"));
        assertTrue(body.asDocument().get("_total_pages").isNumber());

        assertNotNull(body.asDocument().get("_embedded"));

        assertTrue(body.asDocument().get("_embedded").isArray());

        assertTrue(body.asDocument().get("_embedded").asArray().size() == body.asDocument()
                .get("_returned").asNumber().intValue());

        body.asDocument().get("_embedded").asArray().forEach(db -> {
            assertNotNull(db);
            assertNotNull(db.isDocument());
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isArray());

        assertTrue(body.asArray().size() > 0);

        body.asArray().forEach(db -> {
            assertNotNull(db);
            assertNotNull(db.isDocument());
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNotNull(body.asDocument().get("_returned"));
        assertTrue(body.asDocument().get("_returned").isNumber());

        assertNotNull(body.asDocument().get("_size"));
        assertTrue(body.asDocument().get("_size").isNumber());

        assertNotNull(body.asDocument().get("_total_pages"));
        assertTrue(body.asDocument().get("_total_pages").isNumber());

        assertNotNull(body.asDocument().get("_embedded"));

        assertTrue(body.asDocument().get("_embedded").isArray());

        assertTrue(body.asDocument().get("_embedded").asArray().size() == body.asDocument()
                .get("_returned").asNumber().intValue());

        body.asDocument().get("_embedded").asArray().forEach(coll -> {
            assertNotNull(coll);
            assertNotNull(coll.isDocument());
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isArray());

        assertTrue(body.asArray().size() > 0);

        body.asArray().forEach(coll -> {
            assertNotNull(coll);
            assertNotNull(coll.isDocument());
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNotNull(body.asDocument().get("_returned"));
        assertTrue(body.asDocument().get("_returned").isNumber());

        assertTrue(10 == body.asDocument().get("_returned").asNumber().intValue());

        assertNotNull(body.asDocument().get("_size"));
        assertTrue(body.asDocument().get("_size").isNumber());

        assertNotNull(body.asDocument().get("_total_pages"));
        assertTrue(body.asDocument().get("_total_pages").isNumber());

        assertNotNull(body.asDocument().get("_embedded"));

        assertTrue(body.asDocument().get("_embedded").isArray());

        assertTrue(10 == body.asDocument().get("_embedded").asArray().size());

        body.asDocument().get("_embedded").asArray().forEach(doc -> {
            assertNotNull(doc);
            assertNotNull(doc.isDocument());
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNotNull(body.asDocument().get("_returned"));
        assertTrue(body.asDocument().get("_returned").isNumber());

        assertTrue(0 == body.asDocument().get("_returned").asNumber().intValue());

        assertNotNull(body.asDocument().get("_embedded"));

        assertTrue(body.asDocument().get("_embedded").isArray());

        assertTrue(body.asDocument().get("_embedded").asArray().isEmpty());
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isArray());

        assertTrue(body.asArray().isEmpty());

        body.asArray().forEach(doc -> {
            assertNotNull(doc);
            assertNotNull(doc.isDocument());
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNotNull(body.asDocument().get("_returned"));
        assertTrue(body.asDocument().get("_returned").isNumber());

        assertNotNull(body.asDocument().get("_size"));
        assertTrue(body.asDocument().get("_size").isNumber());

        assertNotNull(body.asDocument().get("_embedded"));

        assertTrue(body.asDocument().get("_embedded").isArray());

        assertTrue(body.asDocument().get("_embedded").asArray().size() == body.asDocument()
                .get("_returned").asNumber().intValue());

        body.asDocument().get("_embedded").asArray().forEach(index -> {
            assertNotNull(index);
            assertNotNull(index.isDocument());
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertTrue(body.asDocument().keySet().size() == 3);
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertTrue(body.asDocument().keySet().size() == 3);
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

        assertEquals(HttpStatus.SC_BAD_REQUEST, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNull(body.asDocument().get("_embedded"));

        assertNotNull(body.asDocument().get("exception"));

        assertTrue(body.asDocument().get("exception").isString());

        assertNotNull(body.asDocument().get("exception message"));

        assertTrue(body.asDocument().get("exception message").isString());

        assertNotNull(body.asDocument().get("message"));
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

        assertEquals(HttpStatus.SC_BAD_REQUEST, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNotNull(body.asDocument().get("exception"));

        assertTrue(body.asDocument().get("exception").isString());

        assertNotNull(body.asDocument().get("exception message"));

        assertTrue(body.asDocument().get("exception message").isString());

        assertNotNull(body.asDocument().get("message"));

        assertTrue(body.asDocument().get("message").isString());
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

        assertEquals(HttpStatus.SC_BAD_REQUEST, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNull(body.asDocument().get("_embedded"));

        assertNotNull(body.asDocument().get("exception"));

        assertTrue(body.asDocument().get("exception").isString());

        assertNotNull(body.asDocument().get("exception message"));

        assertTrue(body.asDocument().get("exception message").isString());

        // event if np, in case of error return the props
        assertNotNull(body.asDocument().get("message"));
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        resp = Unirest.get(url(DB, COLL, "_aggrs", "test"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .asString();

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNotNull(body.asDocument().get("_returned"));

        assertTrue(body.asDocument().get("_returned").isNumber());

        assertTrue(body.asDocument().get("_returned").asNumber().intValue() == 5);

        assertNotNull(body.asDocument().get("_embedded"));

        assertTrue(body.asDocument().get("_embedded").isArray());

        assertTrue(body.asDocument().get("_embedded").asArray().size() == 5);

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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        resp = Unirest.get(url(DB, COLL, "_aggrs", "test"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .asString();

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNotNull(body.asDocument().get("_returned"));

        assertTrue(body.asDocument().get("_returned").isNumber());

        assertTrue(body.asDocument().get("_returned").asNumber().intValue() == 0);

        assertNotNull(body.asDocument().get("_embedded"));

        assertTrue(body.asDocument().get("_embedded").isArray());

        assertTrue(body.asDocument().get("_embedded").asArray().isEmpty());
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        resp = Unirest.get(url(DB, COLL, "_aggrs", "test"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("rep", "pj")
                .asString();

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNotNull(body.asDocument().get("_returned"));

        assertTrue(body.asDocument().get("_returned").isNumber());

        assertTrue(body.asDocument().get("_returned").asNumber().intValue() == 0);

        assertNotNull(body.asDocument().get("_embedded"));

        assertTrue(body.asDocument().get("_embedded").isArray());

        assertTrue(body.asDocument().get("_embedded").asArray().isEmpty());
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNotNull(body.asDocument().get("inserted"));

        assertTrue(body.asDocument().get("inserted").isNumber());

        assertTrue(body.asDocument().get("inserted")
                .asNumber().intValue() == 2);

        assertNotNull(body.asDocument().get("_links"));

        assertTrue(body.asDocument().get("_links").isDocument());

        assertNotNull(body.asDocument().get("_links")
                .asDocument().get("rh:newdoc"));

        assertTrue(body.asDocument().get("_links")
                .asDocument().get("rh:newdoc").isArray());

        assertTrue(body.asDocument().get("_links")
                .asDocument().get("rh:newdoc").asArray().size() == 2);

        assertTrue(body.asDocument().keySet().size() == 5);
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

        assertEquals(HttpStatus.SC_OK, resp.getStatus());

        String _body = resp.getBody();

        assertTrue(_body != null && !_body.isEmpty());

        BsonValue body = BsonUtils.parse(_body);

        assertTrue(body.isDocument());

        assertNotNull(body.asDocument().get("inserted"));

        assertTrue(body.asDocument().get("inserted").isNumber());

        assertTrue(body.asDocument().get("inserted").asNumber().intValue() == 0);

        assertNotNull(body.asDocument().get("matched"));

        assertTrue(body.asDocument().get("matched").isNumber());

        assertTrue(body.asDocument().get("matched").asNumber().intValue() == 4);

        assertNotNull(body.asDocument().get("modified"));

        assertTrue(body.asDocument().get("modified").isNumber());

        assertTrue(body.asDocument().get("modified").asNumber().intValue() == 4);

        assertNull(body.asDocument().get("_links"));
    }
}
