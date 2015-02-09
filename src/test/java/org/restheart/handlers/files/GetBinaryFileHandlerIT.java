/*
 * RESTHeart - the data REST API server
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
package org.restheart.handlers.files;

import com.mongodb.MongoClient;
import java.io.File;
import java.nio.file.Path;
import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restheart.Configuration;
import org.restheart.db.MongoDBClientSingleton;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class GetBinaryFileHandlerIT {

    public static final String CONFIG_YML = "etc/restheart-integrationtest.yml";
    public static final String HOST = "localhost";

    static Executor executor = null;
    static MongoClient mongoClient = null;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Path confFilePath = new File(CONFIG_YML).toPath();
        Configuration conf = new Configuration(confFilePath);
        MongoDBClientSingleton.init(conf);

        mongoClient = MongoDBClientSingleton.getInstance().getClient();
        executor = Executor.newInstance().authPreemptive(new HttpHost(HOST, 18080, "HTTP")).auth(new HttpHost(HOST), "admin", "changeit");
    }

    public GetBinaryFileHandlerIT() {
    }

    @Test
    public void testHandleRequest() throws Exception {
        Response resp = executor.execute(Request.Get("http://localhost:18080/filedb/mybucket.files/54c90079300432cf132a6849"));
        Assert.assertEquals(200, resp.returnResponse().getStatusLine().getStatusCode());
    }

}
