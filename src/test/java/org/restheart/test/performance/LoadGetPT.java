/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
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
 * org.restheart.LoadTestRestHeartTask#get -c 20 -n 500 -w 5 -p
 * "url=http://127.0.0.1:8080/testdb/testcoll?page=10&pagesize=5,id=a,pwd=a"
 *
 * @author Andrea Di Cesare
 */
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.restheart.ConfigurationException;
import org.restheart.db.CollectionDAO;
import org.restheart.db.DBCursorPool;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.utils.FileUtils;
import org.restheart.utils.HttpStatus;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 * @author Andrea Di Cesare
 */
public class LoadGetPT {

    private String url;

    private String id;
    private String pwd;
    private boolean printData = false;
    private String db;
    private String coll;

    private final Path CONF_FILE = new File("./etc/restheart-integrationtest.yml").toPath();
    private Executor httpExecutor;

    private final ConcurrentHashMap<Long, Integer> threadPages = new ConcurrentHashMap<>();

    /**
     *
     * @param url
     * @throws MalformedURLException
     */
    public void setUrl(String url) throws MalformedURLException {
        this.url = url;
    }

    /**
     *
     */
    public void prepare() {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(id, pwd.toCharArray());
            }
        });

        try {
            MongoDBClientSingleton.init(FileUtils.getConfiguration(CONF_FILE, false));
        } catch (ConfigurationException ex) {
            System.out.println(ex.getMessage() + ", exiting...");
            System.exit(-1);
        }

        httpExecutor = Executor.newInstance().authPreemptive(new HttpHost("127.0.0.1", 8080, "http")).auth(new HttpHost("127.0.0.1"), id, pwd);
    }

    /**
     *
     * @throws IOException
     */
    public void get() throws IOException {
        URLConnection connection = new URL(url).openConnection();

        //connection.setRequestProperty("Accept-Encoding", "gzip");
        InputStream stream = connection.getInputStream();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String data = in.readLine();

            while (data != null) {
                if (printData) {
                    System.out.println(data);
                }

                data = in.readLine();
            }
        }
    }

    /**
     *
     * @throws IOException
     */
    public void dbdirect() throws IOException {
        DBCollection dbcoll = CollectionDAO.getCollection(db, coll);

        //CollectionDAO.getCollectionSize(coll, null);
        //CollectionDAO.getCollectionProps(coll);
        ArrayList<DBObject> data = CollectionDAO.getCollectionData(dbcoll, 5000, 100, null, null, DBCursorPool.EAGER_CURSOR_ALLOCATION_POLICY.NONE);

        if (printData) {
            System.out.println(data);
        }
    }

    public void getPagesLinearly() throws Exception {
        Integer page = threadPages.get(Thread.currentThread().getId());

        if (page == null) {
            threadPages.put(Thread.currentThread().getId(), 5000);
            page = 5000;
        }

        String pagedUrl = url + "?page=" + (page % 10000);

        page++;
        threadPages.put(Thread.currentThread().getId(), page);

        if (printData) {
            System.out.println(Thread.currentThread().getId() + " -> " + pagedUrl);
        }

        Response resp = httpExecutor.execute(Request.Get(new URI(pagedUrl)));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
    }

    public void getPagesRandomly() throws Exception {

        long rpage = Math.round(Math.random() * 10000);

        String pagedUrl = url + "?page=" + rpage;

        //System.out.println(pagedUrl);
        Response resp = httpExecutor.execute(Request.Get(new URI(pagedUrl)));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @param pwd the pwd to set
     */
    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    /**
     * @param printData the printData to set
     */
    public void setPrintData(String printData) {
        this.printData = Boolean.valueOf(printData);
    }

    /**
     * @param db the db to set
     */
    public void setDb(String db) {
        this.db = db;
    }

    /**
     * @param coll the coll to set
     */
    public void setColl(String coll) {
        this.coll = coll;
    }
}
