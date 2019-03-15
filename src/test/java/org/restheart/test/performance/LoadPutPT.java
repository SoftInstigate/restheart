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
package org.restheart.test.performance;

/**
 * install ldt from https://github.com/bazhenov/load-test-tool run it from
 * target/class directory (current directory is added to classpath) as follows:
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
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.types.ObjectId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.restheart.db.DocumentDAO;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class LoadPutPT extends AbstractPT {

    public void post() throws Exception {
        HttpRequestWithBody req = Unirest.post(url);
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

    public void postWitId() throws Exception {
        HttpRequestWithBody req = Unirest.post(url);
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

    public void put() throws Exception {
        String _url = url + "/" + new ObjectId().toString();

        HttpRequestWithBody req = Unirest.put(_url);
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

    public void putUrl() throws Exception {
        String _url = this.url;

        HttpRequestWithBody req = Unirest.put(_url);
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

    public void dbdirect() throws IOException {
        BsonDocument content = new BsonDocument("random",
                new BsonDouble(Math.random()));

        new DocumentDAO().upsertDocument(
                null, // no client session
                db,
                coll,
                null,
                null,
                null,
                content,
                null,
                false,
                false);
    }
}
