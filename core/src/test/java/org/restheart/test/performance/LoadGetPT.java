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
package org.restheart.test.performance;

/**
 * install ldt from https://github.com/bazhenov/load-test-tool run it from
 * target/class directory (current directory is added to classpath) as follows:
 * <PATH_TO_ldt-assembly-1.1>/bin/ldt.sh -z
 * org.restheart.test.performance.LoadGetPT#get -c 20 -n 500 -w 5 -p
 * "url=http://127.0.0.1:8080/testdb/testcoll?page=10&pagesize=5,id=a,pwd=a"
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.restheart.exchange.ExchangeKeys.EAGER_CURSOR_ALLOCATION_POLICY;
import org.restheart.mongodb.db.Database;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@SuppressWarnings("unchecked")
public class LoadGetPT extends AbstractPT {

    private boolean printData = false;
    private String doc;
    private String filter = null;
    private int page = 1;
    private int pagesize = 5;
    private String eager;

    private final ConcurrentHashMap<Long, Integer> threadPages
            = new ConcurrentHashMap<>();

    /**
     *
     * @throws IOException
     */
    public void get() throws IOException {
        URLConnection connection = new URL(url).openConnection();

        //connection.setRequestProperty("Accept-Encoding", "gzip");
        InputStream stream = connection.getInputStream();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String data = in.readLine();

            while (data != null) {
                if (printData) {
                }

                data = in.readLine();
            }
        }
    }

    /**
     *
     */
    public void dbdirect() {
        final Database dbsDAO = new DatabaseImpl();
        @SuppressWarnings("rawtypes")
        MongoCollection dbcoll = dbsDAO.getCollection(db, coll);

        BsonArray data;

        try {
            data = new DatabaseImpl().getCollectionData(
                    null, // no session
                    dbcoll,
                    page,
                    pagesize,
                    null,
                    BsonDocument.parse(filter),
                    null,
                    null,
                    EAGER_CURSOR_ALLOCATION_POLICY.NONE);
        } catch (Exception e) {
            return;
        }

        assertNotNull(data);
        assertFalse(data.isEmpty());

        if (printData) {
        }
    }

    /**
     *
     */
    public void dbdirectdoc() {
        final Database dbDao = new DatabaseImpl();
        MongoCollection<BsonDocument> dbcoll = dbDao.getCollection(db, coll);

        ObjectId oid;
        String sid;

        if (ObjectId.isValid(doc)) {
            sid = null;
            oid = new ObjectId(doc);
        } else {
            // the id is not an object id
            sid = doc;
            oid = null;
        }

        BasicDBObject query;

        if (oid != null) {
            query = new BasicDBObject("_id", oid);
        } else {
            query = new BasicDBObject("_id", sid);
        }

        BsonDocument data;

        try {
            data = dbcoll.find(query).limit(1).first();
        } catch (Exception e) {
            return;
        }

        assertNotNull(data);

        if (printData) {
        }
    }

    /**
     *
     * @throws Exception
     */
    public void getPagesLinearly() throws Exception {
        Integer _page = threadPages.get(Thread.currentThread().getId());

        if (_page == null) {
            threadPages.put(Thread.currentThread().getId(), page > 0 ? page : 1);
            _page = page;
        }

        String pagedUrl;

        if (url.contains("?")) {
            pagedUrl = url + "&page=" + (_page);
        } else {
            pagedUrl = url + "?page=" + (_page);
        }

        if (getEager() != null) {
            pagedUrl = pagedUrl + "&eager=" + getEager();
        }

        _page++;
        threadPages.put(Thread.currentThread().getId(), _page);

        if (printData) {
        }

        Response resp = httpExecutor.execute(Request.Get(new URI(pagedUrl)));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull("check json not null", json);
        assertNotNull("check not null _returned property", json.get("_returned"));

        if (json.get("_returned").asInt() == 0) {
            System.exit(-1);
        }

        //assertTrue("check _size > 0", json.get("_returned").asInt() > 0);
    }

    /**
     *
     * @throws Exception
     */
    public void getPagesRandomly() throws Exception {

        long rpage = Math.round(Math.random() * 10000);

        String pagedUrl;

        if (url.contains("?")) {
            pagedUrl = url + "&page=" + (rpage);
        } else {
            pagedUrl = url + "?page=" + (rpage);
        }

        //System.out.println(pagedUrl);
        Response resp = httpExecutor.execute(Request.Get(new URI(pagedUrl)));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals(
                "check status code",
                HttpStatus.SC_OK,
                statusLine.getStatusCode());
    }

    /**
     * @param printData the printData to set
     */
    public void setPrintData(String printData) {
        this.printData = Boolean.valueOf(printData);
    }

    /**
     * @param filter the filter to set
     */
    public void setFilter(String filter) {
        this.filter = filter;
    }

    /**
     * @param page the page to set
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * @param pagesize the pagesize to set
     */
    public void setPagesize(int pagesize) {
        this.pagesize = pagesize;
    }

    /**
     * @return the doc
     */
    public String getDoc() {
        return doc;
    }

    /**
     * @param doc the doc to set
     */
    public void setDoc(String doc) {
        this.doc = doc;
    }

    /**
     * @return the eager
     */
    public String getEager() {
        return eager;
    }

    /**
     * @param eager the eager to set
     */
    public void setEager(String eager) {
        this.eager = eager;
    }
}