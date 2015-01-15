/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
 * <PATH_TO_ldt-assembly-1.1>/bin/ldt.sh -z org.restheart.perftest.LoadPutPT#put
 * -c 20 -n 500 -w 5 -p
 * "url=http://127.0.0.1:8080/testdb/testcoll?page=10&pagesize=5,id=a,pwd=a"
 *
 * @author Andrea Di Cesare
 */
import com.mongodb.BasicDBObject;
import io.undertow.util.Headers;
import org.restheart.db.DocumentDAO;
import org.restheart.db.MongoDBClientSingleton;
import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.nio.file.Path;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.restheart.ConfigurationException;
import org.restheart.db.DocumentEntity;
import org.restheart.hal.Representation;
import org.restheart.utils.FileUtils;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare
 */
public class LoadPutPT {

    private String url;

    private String id;
    private String pwd;
    private String db;
    private String coll;

    private static final ContentType halCT = ContentType.create(Representation.HAL_JSON_MEDIA_TYPE);

    private Executor httpExecutor;

    private final Path CONF_FILE = new File("./etc/restheart-perftest.yml").toPath();

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

        httpExecutor = Executor.newInstance();
            // for perf test we disable the restheart security
        //.authPreemptive(new HttpHost("127.0.0.1", 8080, "http")).auth(new HttpHost("127.0.0.1"), id, pwd);
    }

    /**
     *
     * @throws IOException
     */
    public void put() throws Exception {
        BasicDBObject content = new BasicDBObject("random", Math.random());

        Response resp = httpExecutor.execute(Request.Post(url).bodyString(content.toString(), halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_CREATED, statusLine.getStatusCode());
    }

    /**
     *
     * @throws IOException
     */
    public void dbdirect() throws IOException {
        BasicDBObject content = new BasicDBObject("random", Math.random());

        new DocumentDAO().upsert(new DocumentEntity(db, coll, null, content, null, false));
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

    /**
     *
     * @param url
     */
    public void setUrl(String url) {
        this.url = url;
    }
}
