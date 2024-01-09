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

import static org.restheart.test.integration.HttpClientAbstactIT.HTTP;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.restheart.Bootstrapper;
import org.restheart.mongodb.RHMongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mongodb.ConnectionString;

import kong.unirest.Unirest;
import kong.unirest.UnirestException;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class AbstactIT {

    /**
     *
     */
    protected static final Logger LOG = LoggerFactory.getLogger(AbstactIT.class);

    /**
     *
     */
    protected static final String ADMIN_ID = "admin";

    /**
     *
     */
    protected static final String ADMIN_PWD = "secret";

    /**
     * dbs starting with this prefix will be automatically deleted after test
     * execution
     */
    public static final String TEST_DB_PREFIX = "test";

    /**
     *
     */
    protected static final HttpHost HTTP_HOST = new HttpHost("127.0.0.1", 8080, HTTP);

    /**
     *
     */
    public static ConnectionString MONGO_URI = new ConnectionString("mongodb://127.0.0.1");

    static {
        // set the system property test-connection-string to specify a non-default connection string for testing
        try {
            var mongoConnectionString = System.getProperty("test-connection-string");
            if (mongoConnectionString != null) {
                MONGO_URI = new ConnectionString(mongoConnectionString);
            }
        } catch(Throwable t) {
            LOG.warn("wrong property test-connection-string, using default value");
        }

        LOG.info("BASE_URL={}", HTTP_HOST.toURI());
        LOG.info("mongo-uri={}", MONGO_URI.toString());
        RHMongoClients.setClients(com.mongodb.client.MongoClients.create(MONGO_URI), null);
    }

    /**
     *
     * @throws URISyntaxException
     */
    @BeforeAll
    public static void setUpClass() throws URISyntaxException {
    }

    /**
     *
     */
    @AfterAll
    public static void tearDownClass() {
        deleteTestData();
    }

    /**
     *
     * @param resourcePath
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    protected static String getResourceFile(String resourcePath) throws IOException, URISyntaxException {
        StringBuilder result = new StringBuilder();

        // Get file from resources folder
        ClassLoader classLoader = Bootstrapper.class.getClassLoader();

        URI uri = classLoader.getResource(resourcePath).toURI();

        Path path = Paths.get(uri);

        List<String> lines = Files.readAllLines(path);

        lines.stream().forEach(line -> {
            result.append(line);
            result.append("\n");
        });

        return result.toString();
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

        sb.append(HTTP_HOST.toURI());

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

    /**
     *
     */
    @BeforeEach
    public void setUp() {
    }

    /**
     *
     */
    @AfterEach
    public void tearDown() {
        deleteTestData();
    }

    private static void deleteTestData() {
        ArrayList<String> dbNames = new ArrayList<>();

        RHMongoClients.mclient().listDatabaseNames().into(dbNames);

        ArrayList<String> deleted = new ArrayList<>();

        dbNames.stream()
                .filter(db -> db.startsWith(TEST_DB_PREFIX))
                .forEach(dbToDelete -> {
                    RHMongoClients.mclient().getDatabase(dbToDelete).drop();
                    deleted.add(dbToDelete);
                });

        LOG.debug("test data deleted");

        // clear cache
        deleted.stream().forEach(db -> {
            try {
                var resp = Unirest.post(HTTP_HOST.toURI() + "/ic")
                        .basicAuth(ADMIN_ID, ADMIN_PWD)
                        .queryString("db", db)
                        .asJson();

                LOG.debug("deleted test db {}", db);
                LOG.debug("invalidating cache for {}, response {}", db, resp.getStatus());
            } catch (UnirestException ex) {
                LOG.warn("error invalidating cache for delete db {}: {}", db, ex.getMessage());
            }
        });
    }

}
