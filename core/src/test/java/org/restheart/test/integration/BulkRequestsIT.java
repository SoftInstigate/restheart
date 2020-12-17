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

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import java.net.URISyntaxException;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BulkRequestsIT extends AbstactIT {

    private final String DB = TEST_DB_PREFIX + "-bulk-requests-db";
    private final String COLL = "coll";

    @SuppressWarnings("rawtypes")
    HttpResponse resp;

    /**
     *
     * @throws URISyntaxException
     */
    public BulkRequestsIT() throws URISyntaxException {
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

        Assert.assertEquals("create db " + DB, HttpStatus.SC_CREATED, resp.getStatus());

        // create collection
        resp = Unirest.put(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("create collection " + DB.concat("/").concat(COLL), HttpStatus.SC_CREATED, resp.getStatus());
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testBulkPostCreate() throws Exception {
        resp = Unirest.post(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("[{'seq': 1 },{'seq': 2 },{'seq': 3 },{'seq': 4 }]")
                .asString();

        Assert.assertEquals("check response status of create test data", HttpStatus.SC_OK, resp.getStatus());

        JsonValue rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("check response body to be a json object",
                rbody != null
                && rbody.isObject());

        JsonValue embedded = rbody.asObject().get("_embedded");

        Assert.assertTrue("check response body to have _embedded json object",
                embedded != null
                && embedded.isObject());

        JsonValue rhresult = embedded.asObject().get("rh:result");

        Assert.assertTrue("check response body to have rh:result json array",
                rhresult != null
                && rhresult.isArray());

        Assert.assertTrue("check rh:result json array to have 1 element",
                rhresult.asArray().size() == 1);

        JsonValue result = rhresult.asArray().get(0);

        Assert.assertTrue("check rh:result element to be a json object",
                result.isObject());

        JsonValue links = result.asObject().get("_links");

        Assert.assertTrue("check rh:result element to have links json object",
                links != null
                && links.isObject());

        JsonValue newdoc = links.asObject().get("rh:newdoc");

        Assert.assertTrue("check rh:result element links json object to have rh:newdoc array with 4 elements",
                newdoc != null
                && newdoc.isArray()
                && newdoc.asArray().size() == 4);

        JsonValue inserted = result.asObject().get("inserted");

        Assert.assertTrue("check rhresult element to have the 'inserted' numeric property equal to 4",
                inserted != null
                && inserted.isNumber()
                && inserted.asInt() == 4);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testBulkPostUpdate() throws Exception {
        // this creates
        resp = Unirest.post(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("[{'_id': 1 },{'_id': 2 },{'_id': 3 },{'_id': 4 }]")
                .asString();

        Assert.assertEquals("check response status of create test data", HttpStatus.SC_OK, resp.getStatus());

        // this update
        resp = Unirest.post(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("[{'_id': 1 },{'_id': 2 },{'_id': 3 },{'_id': 4 }]")
                .asString();

        Assert.assertEquals("check response status of update test data", HttpStatus.SC_OK, resp.getStatus());

        JsonValue rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("check response body to be a json object",
                rbody != null
                && rbody.isObject());

        JsonValue embedded = rbody.asObject().get("_embedded");

        Assert.assertTrue("check response body to have _embedded json object",
                embedded != null
                && embedded.isObject());

        JsonValue rhresult = embedded.asObject().get("rh:result");

        Assert.assertTrue("check response body to have rh:result json array",
                rhresult != null
                && rhresult.isArray());

        Assert.assertTrue("check rh:result json array to have 1 element",
                rhresult.asArray().size() == 1);

        JsonValue result = rhresult.asArray().get(0);

        Assert.assertTrue("check rh:result element to be a json object",
                result.isObject());

        JsonValue modified = result.asObject().get("modified");

        Assert.assertTrue("check rhresult element to have the 'modified' numeric property equal to 4",
                modified != null
                && modified.isNumber()
                && modified.asInt() == 4);

        JsonValue matched = result.asObject().get("matched");

        Assert.assertTrue("check rhresult element to have the 'matched' numeric property equal to 4",
                matched != null
                && matched.isNumber()
                && matched.asInt() == 4);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testBulkDeleteWithoutFilter() throws Exception {
        // this update
        resp = Unirest.delete(url(DB, COLL, "*"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("check response status of delete without filter qparam", HttpStatus.SC_BAD_REQUEST, resp.getStatus());
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testBulkDelete() throws Exception {
        // create test data
        resp = Unirest.post(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("[{'tobedeleted':true },{'tobedeleted':true },{'tobedeleted':true },{'tobedeleted':false }]")
                .asString();

        Assert.assertEquals("check response status of create test data", HttpStatus.SC_OK, resp.getStatus());

        resp = Unirest.get(url(DB, COLL))
                .queryString("filter", "{'tobedeleted':{'$exists': true}}")
                .queryString("count", "")
                .queryString("np", "")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        JsonValue rbody = Json.parse(resp.getBody().toString());

        JsonValue _size = rbody.asObject().get("_size");

        Assert.assertTrue("check rhresult element to have the '_size' numeric property equal to 4",
                _size != null
                && _size.isNumber()
                && _size.asInt() == 4);

        // this update
        resp = Unirest.delete(url(DB, COLL, "*"))
                .queryString("filter", "{'tobedeleted':true}")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("check status of bulk delete", HttpStatus.SC_OK, resp.getStatus());

        rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("check response body to be a json object",
                rbody != null
                && rbody.isObject());

        JsonValue embedded = rbody.asObject().get("_embedded");

        Assert.assertTrue("check response body to have _embedded json object",
                embedded != null
                && embedded.isObject());

        JsonValue rhresult = embedded.asObject().get("rh:result");

        Assert.assertTrue("check response body to have rh:result json array",
                rhresult != null
                && rhresult.isArray());

        Assert.assertTrue("check rh:result json array to have 1 element",
                rhresult.asArray().size() == 1);

        JsonValue result = rhresult.asArray().get(0);

        Assert.assertTrue("check rh:result element to be a json object",
                result.isObject());

        JsonValue deleted = result.asObject().get("deleted");

        Assert.assertTrue("check rhresult element to have the 'deleted' numeric property equal to 3",
                deleted != null
                && deleted.isNumber()
                && deleted.asInt() == 3);

        resp = Unirest.get(url(DB, COLL))
                .queryString("filter", "{'tobedeleted':{'$exists': true}}")
                .queryString("count", "")
                .queryString("np", "")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("check count request to have the 'count' numeric property equal to 1",
                deleted != null
                && deleted.isNumber()
                && deleted.asInt() == 3);

        JsonValue count = rbody.asObject().get("_size");

        Assert.assertTrue("check rhresult element to have the '_size' numeric property equal to 1",
                count != null
                && count.isNumber()
                && count.asInt() == 1);

    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testBulkPatchWithoutFilter() throws Exception {
        // this update
        resp = Unirest.patch(url(DB, COLL, "*"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("check response status of patch without filter qparam", HttpStatus.SC_BAD_REQUEST, resp.getStatus());
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testBulkPatch() throws Exception {
        // create test data
        resp = Unirest.post(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("[{'tobepatched':true },{'tobepatched':true },{'tobepatched':true },{'tobepatched':false }]")
                .asString();

        Assert.assertEquals("check response status of create test data", HttpStatus.SC_OK, resp.getStatus());

        resp = Unirest.get(url(DB, COLL))
                .queryString("filter", "{'tobepatched':{'$exists': true}}")
                .queryString("count", "")
                .queryString("np", "")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        JsonValue rbody = Json.parse(resp.getBody().toString());

        JsonValue _size = rbody.asObject().get("_size");

        Assert.assertTrue("check rhresult element to have the '_size' numeric property equal to 4",
                _size != null
                && _size.isNumber()
                && _size.asInt() == 4);

        resp = Unirest.patch(url(DB, COLL, "*"))
                .queryString("filter", "{'tobepatched':true}")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'newprop': 0}")
                .asString();

        Assert.assertEquals("check status of bulk patch", HttpStatus.SC_OK, resp.getStatus());

        rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("check response body to be a json object",
                rbody != null
                && rbody.isObject());

        JsonValue embedded = rbody.asObject().get("_embedded");

        Assert.assertTrue("check response body to have _embedded json object",
                embedded != null
                && embedded.isObject());

        JsonValue rhresult = embedded.asObject().get("rh:result");

        Assert.assertTrue("check response body to have rh:result json array",
                rhresult != null
                && rhresult.isArray());

        Assert.assertTrue("check rh:result json array to have 1 element",
                rhresult.asArray().size() == 1);

        JsonValue result = rhresult.asArray().get(0);

        Assert.assertTrue("check rh:result element to be a json object",
                result.isObject());

        JsonValue modified = result.asObject().get("modified");

        Assert.assertTrue("check rhresult element to have the 'modified' numeric property equal to 3",
                modified != null
                && modified.isNumber()
                && modified.asInt() == 3);

        JsonValue matched = result.asObject().get("matched");

        Assert.assertTrue("check rhresult element to have the 'matched' numeric property equal to 3",
                matched != null
                && matched.isNumber()
                && matched.asInt() == 3);

        resp = Unirest.get(url(DB, COLL))
                .queryString("filter", "{'tobepatched':true}")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        rbody = Json.parse(resp.getBody().toString());

        embedded = rbody.asObject().get("_embedded");

        JsonValue rhdoc = embedded.asObject().get("rh:doc");

        JsonValue doc = rhdoc.asArray().get(0);

        JsonValue newprop = doc.asObject().get("newprop");

        Assert.assertTrue("check patched doc to have the 'newprop' property",
                newprop != null
                && newprop.isNumber()
                && newprop.asInt() == 0);
    }
}
