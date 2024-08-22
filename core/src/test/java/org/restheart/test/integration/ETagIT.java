/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ETagIT extends AbstactIT {

    private static final String E_TAG = "ETag";
    private static final String ETAG_DOC_POLICY_REQUIRED = "{'etagDocPolicy': 'required'}";
    private static final String WM = "wm";
    private static final String UPDATE = "update";
    private static final String CHECK_ETAG = "checkEtag";
    private static final String WRONG_ETAG = "wrong etag";
    private static final String CHECK_RESPONSE_STATUS_OF_CREATE_TEST_DATA = "check response status of create test data";
    private static final String IF_MATCH = "If-Match";
    private static final String A_1 = "{'a':1}";
    private static final String DOCID = "docid";
    private static final String UPSERT = "upsert";
    private static final String APPLICATION_JSON = "application/json";
    private static final String CONTENT_TYPE = "content-type";
    private static final String DB = TEST_DB_PREFIX + "-etag-db";
    private static final String DB_REQUIRED = TEST_DB_PREFIX + "-etag-required-db";
    private static final String COLL_REQUIRED = "coll-required";
    private static final String COLL = "coll";

    private HttpResponse<String> resp;

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

        // create test db with required policy
        resp = Unirest.put(url(DB_REQUIRED))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body(ETAG_DOC_POLICY_REQUIRED)
                .asString();

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(), "create db " + DB_REQUIRED);

        // create collection
        resp = Unirest.put(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(),
                formatCollectionCreationMessage());

        // create collection
        resp = Unirest.put(url(DB_REQUIRED, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(),
                formatCollectionCreationMessage());

        // create documents
        resp = Unirest.put(url(DB, COLL, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        // create documents
        resp = Unirest.put(url(DB_REQUIRED, COLL, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString(WM, UPSERT)
                .asString();

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(), "create document " + DB_REQUIRED + "/" + COLL + "/docid");

        // create collection with required etag
        resp = Unirest.put(url(DB, COLL_REQUIRED))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .body(ETAG_DOC_POLICY_REQUIRED)
                .asString();

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(), formatCollectionCreationMessage());

        // create document
        resp = Unirest.put(url(DB, COLL_REQUIRED, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString(WM, UPSERT)
                .asString();

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(), "create document " + DB + "/" + COLL + "/docid");
    }

    private String formatCollectionCreationMessage() {
        return "create collection " + DB + "/" + COLL;
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testUpdateNotRequired() throws Exception {
        // makes ure docid does not exists
        resp = Unirest.delete(url(DB, COLL, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .asString();

        resp = Unirest.put(url(DB, COLL, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .queryString(WM, UPSERT)
                .body(A_1)
                .asString();

        assertEquals(HttpStatus.SC_CREATED,
                resp.getStatus(), CHECK_RESPONSE_STATUS_OF_CREATE_TEST_DATA);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testUpdateRequired() throws Exception {
        resp = Unirest.put(url(DB, COLL_REQUIRED, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .queryString(WM, UPSERT)
                .body(A_1)
                .asString();

        assertEquals(HttpStatus.SC_CONFLICT,
                resp.getStatus(), "check response status of data with missing etag");

        resp = Unirest.put(url(DB, COLL_REQUIRED, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .header(IF_MATCH, WRONG_ETAG)
                .queryString(WM, UPSERT)
                .body(A_1)
                .asString();

        assertEquals(HttpStatus.SC_PRECONDITION_FAILED, resp.getStatus(),
                "check response status of update data with wrong etag");

        String etag = resp.getHeaders().get(E_TAG).get(0);

        resp = Unirest.put(url(DB, COLL_REQUIRED, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .queryString(CHECK_ETAG, "")
                .queryString(WM, UPSERT)
                .header(IF_MATCH, etag)
                .body(A_1)
                .asString();

        assertEquals(HttpStatus.SC_OK,
                resp.getStatus(), "check response status of update data with correct etag");

        // now use the DB_REQUIRED
        resp = Unirest.put(url(DB_REQUIRED, COLL, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .queryString(WM, UPSERT)
                .body(A_1)
                .asString();

        assertEquals(HttpStatus.SC_CONFLICT,
                resp.getStatus(), CHECK_RESPONSE_STATUS_OF_CREATE_TEST_DATA);

        resp = Unirest.put(url(DB_REQUIRED, COLL, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .header(IF_MATCH, WRONG_ETAG)
                .queryString(WM, UPSERT)
                .body(A_1)
                .asString();

        assertEquals(HttpStatus.SC_PRECONDITION_FAILED, resp.getStatus(),
                "check response status of update data without etag");

        etag = resp.getHeaders().get(E_TAG).get(0);

        resp = Unirest.put(url(DB_REQUIRED, COLL, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .queryString(CHECK_ETAG, "")
                .queryString(WM, UPSERT)
                .header(IF_MATCH, etag)
                .body(A_1)
                .asString();

        assertEquals(HttpStatus.SC_OK,
                resp.getStatus(), "check response status of update data with correct etag");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testUpdateEtagQParam() throws Exception {
        // this makes sure that the document docid exists
        resp = Unirest.put(url(DB, COLL, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .queryString(CHECK_ETAG, "")
                .queryString(WM, "insert")
                .body(A_1)
                .asString();

        resp = Unirest.put(url(DB, COLL, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .queryString(CHECK_ETAG, "")
                .queryString(WM, UPDATE)
                .body(A_1)
                .asString();

        assertEquals(HttpStatus.SC_CONFLICT,
                resp.getStatus(), CHECK_RESPONSE_STATUS_OF_CREATE_TEST_DATA);

        resp = Unirest.put(url(DB, COLL, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .queryString(CHECK_ETAG, "")
                .queryString(WM, UPDATE)
                .header(IF_MATCH, WRONG_ETAG)
                .body(A_1)
                .asString();

        assertEquals(HttpStatus.SC_PRECONDITION_FAILED,
                resp.getStatus(), CHECK_RESPONSE_STATUS_OF_CREATE_TEST_DATA);

        final String etag = resp.getHeaders().get(E_TAG).get(0);

        resp = Unirest.put(url(DB, COLL, DOCID))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString(CHECK_ETAG, "")
                .queryString(WM, UPDATE)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .header(IF_MATCH, etag)
                .body(A_1)
                .asString();

        assertEquals(HttpStatus.SC_OK, resp.getStatus(), CHECK_RESPONSE_STATUS_OF_CREATE_TEST_DATA);
    }
}
