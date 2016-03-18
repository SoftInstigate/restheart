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
package org.restheart.test.integration;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mongodb.MongoClient;
import org.restheart.Configuration;
import org.restheart.db.MongoDBClientSingleton;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class AbstactIT {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstactIT.class);

    protected static final String MONGO_HOST = System.getenv("MONGO_HOST") == null ? "127.0.0.1" : System.getenv("MONGO_HOST");
    protected static final Path CONF_FILE_PATH = new File("etc/restheart-integrationtest.yml").toPath();

    protected static MongoClient mongoClient;
    protected static Configuration conf = null;

    protected static final String ADMIN_ID = "admin";
    protected static final String ADMIN_PWD = "changeit";

    /**
     * dbs starting with this prefix will be automatically deleted after test
     * execution
     */
    protected static final String TEST_DB_PREFIX = "restheart-integrationtests-";

    protected static String BASE_URL;

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    @BeforeClass
    public static void setUpClass() throws Exception {
        conf = new Configuration(CONF_FILE_PATH);

        BASE_URL = "http://" + conf.getHttpHost() + ":" + conf.getHttpPort();

        MongoDBClientSingleton.init(conf);

        mongoClient = MongoDBClientSingleton.getInstance().getClient();
    }

    @AfterClass
    public static void tearDownClass() {
    }

    public AbstactIT() {
    }

    protected static String getResourceFile(String resourcePath) throws IOException, URISyntaxException {
        StringBuilder result = new StringBuilder();

        //Get file from resources folder
        ClassLoader classLoader = JsonPathConditionsCheckerIT.class.getClassLoader();

        URI uri = classLoader.getResource(resourcePath).toURI();

        Path path = Paths.get(uri);

        List<String> lines = Files.readAllLines(path);

        lines.stream().forEach(line -> {
            result.append(line);
            result.append("\n");
        });

        return result.toString();
    }

    @After
    public void tearDown() {
        deleteTestData();
    }

    private void deleteTestData() {
        ArrayList<String> dbNames = new ArrayList<>();

        MongoDBClientSingleton.getInstance()
                .getClient()
                .listDatabaseNames()
                .into(dbNames);

        ArrayList<String> deleted = new ArrayList<>();

        dbNames
                .stream()
                .filter(db -> db.startsWith(TEST_DB_PREFIX))
                .forEach(dbToDelete -> {
                    MongoDBClientSingleton.getInstance().getClient().dropDatabase(dbToDelete);
                    deleted.add(dbToDelete);
                });

        LOG.debug("test data deleted");

        // clear cache
        if (conf.isLocalCacheEnabled()) {
            deleted.stream().forEach(db -> {
                HttpResponse resp;
                try {
                    resp = Unirest.post(BASE_URL + "/_logic/ic")
                            .basicAuth(ADMIN_ID, ADMIN_PWD)
                            .queryString("db", db)
                            .asJson();

                    LOG.debug("invalidating cache for {}, repospose {}", db, resp.getStatus());
                } catch (UnirestException ex) {
                    LOG.warn("error invalidating cache for delete db {}", db, ex);
                }
            });
        }
    }

    /**
     * returns the url composed of the parts note the db anem will be prefixed
     * with TEST_DB_PREFIX and thus deleted after test execution
     *
     * @param dbname
     * @param parts
     * @return
     */
    protected static String url(String dbname, String... parts) {
        StringBuilder sb = new StringBuilder();

        sb.append(BASE_URL);

        if (dbname != null) {
            sb.append("/")
                    .append(TEST_DB_PREFIX)
                    .append(dbname);

            if (parts != null) {
                for (String part : parts) {
                    sb.append("/").append(part);
                }
            }
        }

        return sb.toString();
    }
}
