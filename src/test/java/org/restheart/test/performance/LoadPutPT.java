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
import com.mongodb.BasicDBObject;
import io.undertow.util.Headers;
import org.restheart.db.DocumentDAO;
import java.io.IOException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.restheart.hal.Representation;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class LoadPutPT extends AbstractPT {
    private static final ContentType halCT = ContentType.create(Representation.HAL_JSON_MEDIA_TYPE);

    /**
     *
     * @throws IOException
     */
    public void put() throws Exception {
        BasicDBObject content = new BasicDBObject("nanostamp", System.nanoTime());

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

        new DocumentDAO().upsertDocument(db, coll, null, null, content, null, false, false);
    }
}