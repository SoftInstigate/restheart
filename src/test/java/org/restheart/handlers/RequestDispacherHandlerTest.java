package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import static org.hamcrest.CoreMatchers.instanceOf;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.restheart.Configuration;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.handlers.collection.GetCollectionHandler;
import org.restheart.handlers.collection.PutCollectionHandler;
import org.restheart.handlers.database.GetDBHandler;
import org.restheart.handlers.files.PostBucketHandler;
import org.restheart.handlers.root.GetRootHandler;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
@Ignore
public class RequestDispacherHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(RequestDispacherHandlerTest.class);

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    private RequestDispacherHandler dispacher;

    public RequestDispacherHandlerTest() {
    }

    @Before
    public void setUp() {
        MongoDBClientSingleton.init(new Configuration());
        dispacher = new RequestDispacherHandler(false);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testCreateHttpHandlers() {
        dispacher.putPipedHttpHandler(RequestContext.TYPE.ROOT, RequestContext.METHOD.GET, new GetRootHandler(null, null));
        dispacher.putPipedHttpHandler(RequestContext.TYPE.DB, RequestContext.METHOD.GET, new GetDBHandler(null, null));
        dispacher.putPipedHttpHandler(RequestContext.TYPE.COLLECTION, RequestContext.METHOD.PUT, new PutCollectionHandler(null, null));
        dispacher.putPipedHttpHandler(RequestContext.TYPE.FILES_BUCKET, RequestContext.METHOD.GET, new GetCollectionHandler(null, null));

        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.ROOT, RequestContext.METHOD.GET), instanceOf(GetRootHandler.class));
        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.DB, RequestContext.METHOD.GET), instanceOf(GetDBHandler.class));
        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.COLLECTION, RequestContext.METHOD.PUT), instanceOf(PutCollectionHandler.class));
        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.FILES_BUCKET, RequestContext.METHOD.GET), instanceOf(GetCollectionHandler.class));

        assertNull(dispacher.getPipedHttpHandler(RequestContext.TYPE.COLLECTION, RequestContext.METHOD.POST));
    }

    @Ignore
    public void testPostBinaryFileHandler() throws Exception {
        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setRequestPath("/testdb/mybucket.files");
        exchange.setRequestMethod(new HttpString("POST"));
        RequestContext context = new RequestContext(exchange, "/", "*");

        dispacher.putPipedHttpHandler(RequestContext.TYPE.FILES_BUCKET, RequestContext.METHOD.POST, new PostBucketHandler(null, null));
        dispacher.handleRequest(exchange, context);

        assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, exchange.getResponseCode());
    }
}
