/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */


import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mongodb.MongoClientURI;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpHost;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class AbstactIT {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstactIT.class);

    protected static final String HTTP = "http";
    
    protected static final String ADMIN_ID = "admin";
    protected static final String ADMIN_PWD = "changeit";

    /**
     * dbs starting with this prefix will be automatically deleted after test
     * execution
     */
    public static final String TEST_DB_PREFIX = "test";

    protected static final HttpHost HTTP_HOST = new HttpHost("127.0.0.1", 8080, HTTP);

    static {
        LOG.info("BASE_URL={}", HTTP_HOST.toURI());
        LOG.info("mongo-uri={}", "mongodb://127.0.0.1");
        MongoClientSingleton.init(new MongoClientURI("mongodb://127.0.0.1"), null);
    }

    @BeforeClass
    public static void setUpClass() throws URISyntaxException {
    }

    @AfterClass
    public static void tearDownClass() {
        deleteTestData();
    }

    protected static String getResourceFile(String resourcePath) throws IOException, URISyntaxException {
        StringBuilder result = new StringBuilder();

        //Get file from resources folder
        ClassLoader classLoader = AbstactIT.class.getClassLoader();

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
    
    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
        deleteTestData();
    }

    private static void deleteTestData() {
        ArrayList<String> dbNames = new ArrayList<>();

        MongoClientSingleton.getInstance()
                .getClient()
                .listDatabaseNames()
                .into(dbNames);

        ArrayList<String> deleted = new ArrayList<>();

        dbNames
                .stream()
                .filter(db -> db.startsWith(TEST_DB_PREFIX))
                .forEach(dbToDelete -> {
                    MongoClientSingleton.getInstance().getClient().dropDatabase(dbToDelete);
                    deleted.add(dbToDelete);
                });

        LOG.debug("test data deleted");

        // clear cache
        deleted.stream().forEach(db -> {
            try {
                HttpResponse resp = Unirest.post(HTTP_HOST.toURI() + "/ic")
                        .basicAuth(ADMIN_ID, ADMIN_PWD)
                        .queryString("db", db)
                        .asJson();

                LOG.debug("invalidating cache for {}, response {}", db, resp.getStatus());
            } catch (UnirestException ex) {
                LOG.warn("error invalidating cache for delete db {}: {}", db, ex.getMessage());
            }
        });
    }

}
