/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
package org.restheart.test.performance;

/**
 * install ldt from https://github.com/bazhenov/load-test-tool
 *
 * copy deps to target: mvn install dependency:copy-dependencies
 *
 * Modify ldt.sh to add all build, test and dependecy jars to classpath
 *
 * run it as follows:
 *
 * <PATH_TO_ldt-assembly-1.1>/bin/ldt.sh -z
 * org.restheart.test.performance.LoadPutPT#put -c 20 -n 500 -w 5 -p
 * "url=http://127.0.0.1:8080/testdb/testcoll,id=a,pwd=a"
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.HttpRequestWithBody;

import java.io.IOException;
import java.util.Optional;

import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.types.ObjectId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;
import org.restheart.mongodb.db.Documents;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class LoadPutPT extends AbstractPT {

    /**
     *
     * @throws Exception
     */
    public void post() throws Exception {
        HttpRequestWithBody req = Unirest.post(url);
        @SuppressWarnings("rawtypes")
        HttpResponse resp;

        if (id != null && pwd != null) {
            req.basicAuth(id, pwd);
        }

        resp = req
                .header("content-type", "application/json")
                .body("{'nanostamp': " + System.nanoTime() + "}")
                .asString();

        assertEquals("check status code",
                HttpStatus.SC_CREATED, resp.getStatus());
    }

    /**
     *
     * @throws Exception
     */
    public void postWitId() throws Exception {
        HttpRequestWithBody req = Unirest.post(url);
        @SuppressWarnings("rawtypes")
        HttpResponse resp;

        if (id != null && pwd != null) {
            req.basicAuth(id, pwd);
        }

        resp = req
                .header("content-type", "application/json")
                .body("{'_id': {'$oid': '"
                        + new ObjectId().toString()
                        + "'}, 'nanostamp': "
                        + System.nanoTime() + "}")
                .asString();

        assertEquals(
                "check status code",
                HttpStatus.SC_CREATED,
                resp.getStatus());
    }

    /**
     *
     * @throws Exception
     */
    public void put() throws Exception {
        String _url = url + "/" + new ObjectId().toString();

        HttpRequestWithBody req = Unirest.put(_url);

        @SuppressWarnings("rawtypes")
        HttpResponse resp;

        if (id != null && pwd != null) {
            req.basicAuth(id, pwd);
        }

        resp = req
                .header("content-type", "application/json")
                .body("{'nanostamp': " + System.nanoTime() + "}")
                .asString();

        assertEquals(
                "check status code",
                HttpStatus.SC_CREATED,
                resp.getStatus());
    }

    /**
     *
     * @throws Exception
     */
    public void putUrl() throws Exception {
        String _url = this.url;

        HttpRequestWithBody req = Unirest.put(_url);

        @SuppressWarnings("rawtypes")
        HttpResponse resp;

        if (id != null && pwd != null) {
            req.basicAuth(id, pwd);
        }

        resp = req
                .header("content-type", "application/json")
                .body("{'nanostamp': " + System.nanoTime() + "}")
                .asString();

        assertTrue(
                "check status code",
                resp.getStatus() == HttpStatus.SC_CREATED
                || resp.getStatus() == HttpStatus.SC_OK);
    }

    /**
     *
     * @throws IOException
     */
    public void dbdirect() throws IOException {
        var content = new BsonDocument("random",  new BsonDouble(Math.random()));

        Documents.get().writeDocument(
                Optional.empty(), // no client session
                Optional.empty(), // no Replica Set options
                db,
                coll,
                METHOD.POST,
                WRITE_MODE.UPSERT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                content,
                null,
                false);
    }
}
