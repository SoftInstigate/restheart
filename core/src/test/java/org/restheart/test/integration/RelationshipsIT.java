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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RelationshipsIT extends AbstactIT {

    private final String DB = TEST_DB_PREFIX + "-rel-db";
    private final String COLL_PARENT = "parent";
    private final String COLL_CHILDREN = "children";

    HttpResponse<String> resp;

    /**
     *
     */
    public RelationshipsIT() {
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

        String body = "{'rels': ["
                + "{ "
                + "'ref-field': '$.children.[*]', "
                + "'rel': 'children', "
                + "'role': 'OWNING', "
                + "'target-coll': 'children', "
                + "'type':'MANY_TO_MANY' "
                + "}]}";

        // create parent collection
        resp = Unirest.put(url(DB, COLL_PARENT))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body(body)
                .asString();

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(),
                "create collection ".concat(DB.concat("/").concat(COLL_PARENT)));

        // create children collection
        resp = Unirest.put(url(DB, COLL_CHILDREN))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(),
                "create collection ".concat(DB.concat("/").concat(COLL_CHILDREN)));

        // create 10 test children docs
        for (int i = 0; i < 10; i++) {
            resp = Unirest.post(url(DB, COLL_CHILDREN))
                    .basicAuth(ADMIN_ID, ADMIN_PWD)
                    .header("content-type", "application/json")
                    .body("{'_id': " + i + "}")
                    .asString();

            assertEquals(HttpStatus.SC_CREATED, resp.getStatus(), "create doc " + i);
        }

        // creat 1 parent document
        resp = Unirest.put(url(DB, COLL_PARENT, "parent"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .body("{'children': [0,1,2,3,4,5,6,7,8,9]}")
                .asString();

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus(), "create parent doc");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetParent() throws Exception {
        resp = Unirest.get(url(DB, COLL_PARENT, "parent"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        JsonValue rbody = Json.parse(resp.getBody());

        assertTrue(rbody != null
                && rbody.isObject()
                && rbody.asObject().get("_links") != null
                && rbody.asObject().get("_links").isObject()
                && rbody.asObject().get("_links").asObject().get("children") != null
                && rbody.asObject().get("_links").asObject().get("children").isObject()
                && rbody.asObject().get("_links").asObject().get("children").asObject()
                        .get("href") != null
                && rbody.asObject().get("_links").asObject().get("children").asObject()
                        .get("href").isString(),
                "check _links");

        String childrenUrl = rbody.asObject().get("_links").asObject().get("children").asObject().get("href")
                .asString();

        assertTrue(childrenUrl.endsWith("filter={'_id':{'$in':[0,1,2,3,4,5,6,7,8,9]}}"), "check href");
    }
}
