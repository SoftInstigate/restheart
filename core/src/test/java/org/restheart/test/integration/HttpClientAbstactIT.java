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
package org.restheart.test.integration;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.BeforeClass;
import org.restheart.exchange.Exchange;
import org.restheart.exchange.ExchangeKeys.WRITE_MODE;
import org.restheart.mongodb.db.Database;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.db.DocumentDAO;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class HttpClientAbstactIT extends AbstactIT {

    /**
     *
     */
    protected static final String HTTP = "http";

    /**
     *
     */
    protected static Executor adminExecutor = null;

    /**
     *
     */
    protected static Executor user1Executor = null;

    /**
     *
     */
    protected static Executor user2Executor = null;

    /**
     *
     */
    protected static Executor unauthExecutor = null;

    /**
     *
     */
    protected static URI rootUri;

    /**
     *
     */
    protected static URI rootUriRemapped;

    /**
     *
     */
    protected static URI dbUri;

    /**
     *
     */
    protected static URI dbUriRemappedAll;

    /**
     *
     */
    protected static URI dbUriRemappedDb;

    /**
     *
     */
    protected static final String dbName = TEST_DB_PREFIX + "db";

    /**
     *
     */
    protected static URI dbTmpUri;

    /**
     *
     */
    protected static final String dbTmpName = TEST_DB_PREFIX + "mytmpdb";

    /**
     *
     */
    protected static URI dbTmpUri2;

    /**
     *
     */
    protected static final String dbTmpName2 = TEST_DB_PREFIX + "tmpdb2";

    /**
     *
     */
    protected static URI dbTmpUri3;

    /**
     *
     */
    protected static final String dbTmpName3 = TEST_DB_PREFIX + "tmpdb3";

    /**
     *
     */
    protected static URI collection1Uri;

    /**
     *
     */
    protected static URI collection1UriRemappedAll;

    /**
     *
     */
    protected static URI collection1UriRemappedDb;

    /**
     *
     */
    protected static URI collection1UriRemappedCollection;

    /**
     *
     */
    protected static final String collection1Name = "refcoll1";

    /**
     *
     */
    protected static URI collection2Uri;

    /**
     *
     */
    protected static URI collection2UriRemappedAll;

    /**
     *
     */
    protected static URI collection2UriRemappedDb;

    /**
     *
     */
    protected static URI collection2UriRemappedCollection;

    /**
     *
     */
    protected static final String collection2Name = "refcoll2";

    /**
     *
     */
    protected static URI collectionTmpUri;

    /**
     *
     */
    protected static final String collectionTmpName = "tmpcoll";

    /**
     *
     */
    protected static URI collectionTmpUserUri2;

    /**
     *
     */
    protected static URI collectionTmpUserUri3;

    /**
     *
     */
    protected static final String collectionTmpUserName2 = "user2";

    /**
     *
     */
    protected static URI docsCollectionUri;

    /**
     *
     */
    protected static URI docsCollectionUriPaging;

    /**
     *
     */
    protected static URI docsCollectionUriCountAndPaging;

    /**
     *
     */
    protected static URI docsCollectionUriSort;

    /**
     *
     */
    protected static URI docsCollectionUriFilter;

    /**
     *
     */
    protected static final String docsCollectionName = "bandleaders";

    /**
     *
     */
    protected static URI indexesUri;

    /**
     *
     */
    protected static URI indexesUriRemappedAll;

    /**
     *
     */
    protected static URI indexesUriRemappedDb;

    /**
     *
     */
    protected static URI document1Uri;

    /**
     *
     */
    protected static URI dbUriPaging;

    /**
     *
     */
    protected static URI document1UriRemappedAll;

    /**
     *
     */
    protected static URI document1UriRemappedDb;

    /**
     *
     */
    protected static URI document1UriRemappedCollection;

    /**
     *
     */
    protected static URI document1UriRemappedDocument;

    /**
     *
     */
    protected static URI document2Uri;

    /**
     *
     */
    protected static URI document2UriRemappedAll;

    /**
     *
     */
    protected static URI document2UriRemappedDb;

    /**
     *
     */
    protected static URI document2UriRemappedCollection;

    /**
     *
     */
    protected static URI document2UriRemappedDocument;

    /**
     *
     */
    protected static URI documentTmpUri;

    /**
     *
     */
    protected static URI indexesTmpUri;

    /**
     *
     */
    protected static URI indexTmpUri;

    /**
     *
     */
    protected static URI metricsUri;

    /**
     *
     */
    protected static URI metricsUnknownCollectionUri;

    /**
     *
     */
    protected static final String document1Id = "doc1";

    /**
     *
     */
    protected static final String document2Id = "doc2";

    /**
     *
     */
    protected static final String documentTmpId = "tmpdoc";

    /**
     *
     */
    protected static final String dbPropsString = "{ \"a\": 1, \"b\": \"two\", \"c\": { \"d\": 3, \"f\": [\"g\",\"h\",4,{\"i\":5, \"l\":\"six\"}]}}";

    /**
     *
     */
    protected static final String coll1PropsString = "{ \"a\":1, \"rels\" :  ["
            + "{ \"rel\": \"oto\", \"type\": \"ONE_TO_ONE\",  \"role\": \"OWNING\", \"target-coll\": \"refcoll2\", \"ref-field\": \"oto\" },"
            + "{ \"rel\": \"otm\", \"type\": \"ONE_TO_MANY\", \"role\": \"OWNING\", \"target-coll\": \"refcoll2\", \"ref-field\": \"otm\" },"
            + "{ \"rel\": \"mto\", \"type\": \"MANY_TO_ONE\", \"role\": \"OWNING\", \"target-coll\": \"refcoll2\", \"ref-field\": \"mto\" },"
            + "{ \"rel\": \"mtm\", \"type\": \"MANY_TO_MANY\", \"role\": \"OWNING\", \"target-coll\": \"refcoll2\", \"ref-field\": \"mtm\" }"
            + "]}";

    /**
     *
     */
    protected static final String coll2PropsString = "{ \"a\":2, \"rels\" :  ["
            + "{ \"rel\": \"oto\", \"type\": \"ONE_TO_ONE\",  \"role\": \"INVERSE\", \"target-coll\": \"refcoll1\", \"ref-field\": \"oto\" },"
            + "{ \"rel\": \"mto\", \"type\": \"MANY_TO_ONE\", \"role\": \"INVERSE\", \"target-coll\": \"refcoll1\", \"ref-field\": \"otm\" },"
            + "{ \"rel\": \"otm\", \"type\": \"ONE_TO_MANY\", \"role\": \"INVERSE\", \"target-coll\": \"refcoll1\", \"ref-field\": \"mto\" },"
            + "{ \"rel\": \"mtm\", \"type\": \"MANY_TO_MANY\", \"role\": \"INVERSE\", \"target-coll\": \"refcoll1\", \"ref-field\": \"mtm\" }"
            + "]}";

    /**
     *
     */
    protected static final ContentType halCT = ContentType.create(Exchange.HAL_JSON_MEDIA_TYPE);

    /**
     *
     */
    protected static String docsCollectionPropsStrings = "{}";

    /**
     *
     */
    protected static final String collTmpPropsString = "{ \"a\":1 }";

    /**
     *
     */
    protected static final String document1PropsString = "{ \"a\": 1, \"oto\": \"doc2\", \"otm\" : [ \"doc2\" ], \"mto\" : \"doc2\", \"mtm\" : [ \"doc2\" ] }";

    /**
     *
     */
    protected static final String document2PropsString = "{ \"a\": 2 }";

    /**
     *
     */
    protected static BsonDocument dbProps = BsonDocument.parse(HttpClientAbstactIT.dbPropsString);

    /**
     *
     */
    protected static BsonDocument coll1Props = BsonDocument.parse(HttpClientAbstactIT.coll1PropsString);

    /**
     *
     */
    protected static BsonDocument coll2Props = BsonDocument.parse(HttpClientAbstactIT.coll2PropsString);

    /**
     *
     */
    protected static BsonDocument collTmpProps = BsonDocument.parse(HttpClientAbstactIT.collTmpPropsString);

    /**
     *
     */
    protected static BsonDocument docsCollectionProps = BsonDocument.parse(HttpClientAbstactIT.docsCollectionPropsStrings);

    /**
     *
     */
    protected static BsonDocument document1Props = BsonDocument.parse(HttpClientAbstactIT.document1PropsString);

    /**
     *
     */
    protected static BsonDocument document2Props = BsonDocument.parse(HttpClientAbstactIT.document2PropsString);

    /**
     *
     */
    protected static final String[] docsPropsStrings = {
        "{ \"ranking\": 1, \"name\": \"Nick\", \"surname\": \"Cave\", \"band\": \"Nick Cave & the Bad Seeds\"}",
        "{ \"ranking\": 2, \"name\": \"Robert\", \"surname\": \"Smith\", \"band\": \"The Cure\"}",
        "{ \"ranking\": 3, \"name\": \"Leonard\", \"surname\": \"Cohen\", \"band\": \"Leonard Cohen\"}",
        "{ \"ranking\": 4, \"name\": \"Tom\", \"surname\": \"Yorke\", \"band\": \"Radiohead\"}",
        "{ \"ranking\": 5, \"name\": \"Roger\", \"surname\": \"Waters\", \"band\": \"Pink Floyd\"}",
        "{ \"ranking\": 6, \"name\": \"Morrissey\", \"surname\": null, \"band\": \"The Smiths\"}",
        "{ \"ranking\": 7, \"name\": \"Mark\", \"surname\": \"Knopfler\", \"band\": \"Dire Straits\"}",
        "{ \"ranking\": 8, \"name\": \"Ramone\", \"surname\": \"Ramone\", \"band\": \"Ramones\"}",
        "{ \"ranking\": 9, \"name\": \"Ian\", \"surname\": \"Astbury\", \"band\": \"The Cult\"}",
        "{ \"ranking\": 10, \"name\": \"Polly Jean\", \"surname\": \"Harvey\", \"band\": \"PJ Harvey\"}",};
    // { keys: {a:1, b:-1} }

    /**
     *
     */
    protected static final String[] docsCollectionIndexesStrings = {
        "{ \"name\": 1 }",
        "{ \"surname\": 1 }",
        "{ \"band\": 1 }",
        "{ \"ranking\": 1 }"
    };
    private static final String _INDEXES = "/_indexes";
    private static final String REMAPPEDDOC1 = "/remappeddoc1";
    private static final String REMAPPEDALL = "/remappedall";
    private static final String REMAPPEDDB = "/remappeddb";
    private static final String REMAPPEDREFCOLL2 = "/remappedrefcoll2";
    private static final String REMAPPEDDOC2 = "/remappeddoc2";
    private static final String REMAPPEDREFCOLL1 = "/remappedrefcoll1";

    /**
     *
     * @throws URISyntaxException
     */
    @BeforeClass
    public static void setUpClass() throws URISyntaxException {

        createURIs();

        final HttpHost host = new HttpHost(HTTP_HOST.getHostName());

        HttpClientBuilder clientBuilder = HttpClients.custom();
        clientBuilder.setRetryHandler(new DefaultHttpRequestRetryHandler(5, false));
        CloseableHttpClient httpClient = clientBuilder.build();

        adminExecutor = Executor.newInstance(httpClient).authPreemptive(HTTP_HOST).auth(host, "admin", "secret");
        user1Executor = Executor.newInstance().authPreemptive(HTTP_HOST).auth(host, "user1", "secret");
        user2Executor = Executor.newInstance().authPreemptive(HTTP_HOST).auth(host, "user2", "secret");
        unauthExecutor = Executor.newInstance();
    }

    /**
     *
     */
    @AfterClass
    public static void tearDownClass() {
    }

    private static void createURIs() throws URISyntaxException {
        rootUri = buildURI("/", new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });
        rootUriRemapped = buildURI(REMAPPEDALL, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        dbUri = buildURI("/" + dbName, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        dbUriPaging = buildURI("/" + dbName,
                new NameValuePair[]{
                    new BasicNameValuePair("pagesize", "1"),
                    new BasicNameValuePair("hal", "f")
                });

        dbUriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });
        dbUriRemappedDb = buildURI(REMAPPEDDB, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        dbTmpUri = buildURI("/" + dbTmpName);

        dbTmpUri2 = buildURI("/" + dbTmpName2, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });
        dbTmpUri3 = buildURI("/" + dbTmpName3, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        collection1Uri = buildURI("/" + dbName + "/" + collection1Name, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });
        collection1UriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName + "/" + collection1Name, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });
        collection1UriRemappedDb = buildURI(REMAPPEDDB + "/" + collection1Name, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });
        collection1UriRemappedCollection = buildURI(REMAPPEDREFCOLL1, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        collection2Uri = buildURI("/" + dbName + "/" + collection2Name, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        collection2UriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName + "/" + collection2Name, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        collection2UriRemappedDb = buildURI(REMAPPEDDB + "/" + collection2Name, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });
        collection2UriRemappedCollection = buildURI(REMAPPEDREFCOLL2, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        collectionTmpUri = buildURI("/" + dbTmpName + "/" + collectionTmpName, new NameValuePair[]{
            new BasicNameValuePair("hal", "f"),
            new BasicNameValuePair("wm", "upsert"),
        });

        collectionTmpUserUri2 = buildURI("/" + dbTmpName2 + "/" + collectionTmpUserName2, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        collectionTmpUserUri3 = buildURI("/" + dbTmpName3 + "/" + collectionTmpUserName2, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        docsCollectionUri = buildURI("/" + dbName + "/" + docsCollectionName, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        docsCollectionUriPaging = buildURI("/" + dbName + "/" + docsCollectionName,
                new NameValuePair[]{
                    new BasicNameValuePair("pagesize", "2"),
                    new BasicNameValuePair("hal", "f")
                });

        docsCollectionUriCountAndPaging = buildURI("/" + dbName + "/" + docsCollectionName,
                new NameValuePair[]{
                    new BasicNameValuePair("count", null),
                    new BasicNameValuePair("page", "2"),
                    new BasicNameValuePair("pagesize", "2"),
                    new BasicNameValuePair("hal", "f")
                });

        docsCollectionUriSort = buildURI("/" + dbName + "/" + docsCollectionName,
                new NameValuePair[]{
                    new BasicNameValuePair("sort_by", "surname"),
                    new BasicNameValuePair("hal", "f")
                });

        docsCollectionUriFilter = buildURI("/" + dbName + "/" + docsCollectionName,
                new NameValuePair[]{
                    new BasicNameValuePair("filter", "{'name':{'$regex':'.*k$'}}"),
                    new BasicNameValuePair("sort_by", "name"),
                    new BasicNameValuePair("count", null),
                    new BasicNameValuePair("hal", "f")
                });

        indexesUri = buildURI("/" + dbName + "/" + docsCollectionName + _INDEXES, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        indexesUriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName + "/" + docsCollectionName + _INDEXES, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        indexesUriRemappedDb = buildURI(REMAPPEDDB + "/" + docsCollectionName + _INDEXES, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });
        indexesTmpUri = buildURI("/" + dbTmpName + "/" + collectionTmpName + _INDEXES, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        indexTmpUri = buildURI("/" + dbTmpName + "/" + collectionTmpName + _INDEXES + "/new-index", new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        documentTmpUri = buildURI("/" + dbTmpName + "/" + collectionTmpName + "/" + documentTmpId, new NameValuePair[]{
            new BasicNameValuePair("hal", "f"),
            new BasicNameValuePair("wm", "upsert")
        });

        document1Uri = buildURI("/" + dbName + "/" + collection1Name + "/" + document1Id, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        document1UriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName + "/" + collection1Name + "/" + document1Id, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        document1UriRemappedDb = buildURI(REMAPPEDDB + "/" + collection1Name + "/" + document1Id, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        document1UriRemappedCollection = buildURI(REMAPPEDREFCOLL1 + "/" + document1Id, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        document1UriRemappedDocument = buildURI(REMAPPEDDOC1, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        document2Uri = buildURI("/" + dbName + "/" + collection2Name + "/" + document2Id, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        document2UriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName + "/" + collection1Name + "/" + document2Id, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        document2UriRemappedDb = buildURI(REMAPPEDDB + "/" + collection2Name + "/" + document2Id, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        document2UriRemappedCollection = buildURI(REMAPPEDREFCOLL2 + "/" + document2Id, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });

        document2UriRemappedDocument = buildURI(REMAPPEDDOC2, new NameValuePair[]{
            new BasicNameValuePair("hal", "f")
        });
        metricsUri = buildURI("/_metrics");
        metricsUnknownCollectionUri = buildURI("/someunknowndb/unknowncollection/_metrics");
    }

    /**
     *
     * @param path
     * @param parameters
     * @return
     * @throws URISyntaxException
     */
    protected static URI buildURI(String path, NameValuePair[] parameters) throws URISyntaxException {
        return createURIBuilder(path)
                .addParameters(Arrays.asList(parameters))
                .build();
    }

    /**
     *
     * @param path
     * @return
     * @throws URISyntaxException
     */
    protected static URI buildURI(String path) throws URISyntaxException {
        return createURIBuilder(path)
                .build();
    }

    private static URIBuilder createURIBuilder(String path) {
        return new URIBuilder()
                .setScheme(HTTP)
                .setHost(HTTP_HOST.getHostName())
                .setPort(HTTP_HOST.getPort())
                .setPath(path);
    }

    /**
     *
     * @param uri
     * @return
     * @throws URISyntaxException
     */
    protected static URI addCheckEtag(URI uri) throws URISyntaxException {
        return createURIBuilder(uri.getPath())
                .addParameter("checkEtag", null)
                .addParameter("hal", "f")
                .build();
    }
    private final Database dbsDAO = new DatabaseImpl();

    /**
     *
     */
    public HttpClientAbstactIT() {
    }

    /**
     *
     */
    @Before
    @Override
    public void setUp() {
        createTestData();
    }

    /**
     *
     * @param message
     * @param resp
     * @param expectedCode
     * @return
     * @throws IOException
     */
    protected HttpResponse check(String message, Response resp, int expectedCode) throws IOException {
        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);
        assertEquals(message, expectedCode, statusLine.getStatusCode());
        return httpResp;
    }

    private void createTestData() {
        dbsDAO.upsertDB(null, dbName, dbProps, new ObjectId().toString(), false, false, false);

        dbsDAO.upsertCollection(null, dbName, collection1Name, coll1Props, new ObjectId().toString(), false, false, false);
        dbsDAO.upsertCollection(null, dbName, collection2Name, coll2Props, new ObjectId().toString(), false, false, false);
        dbsDAO.upsertCollection(null, dbName, docsCollectionName, docsCollectionProps, new ObjectId().toString(), false, false, false);

        for (String index : docsCollectionIndexesStrings) {
            dbsDAO.createIndex(null, dbName, docsCollectionName, BsonDocument.parse(index), null);
        }

        final DocumentDAO documentDAO = new DocumentDAO();
        documentDAO.writeDocument(null, dbName, collection1Name, document1Id, null, null, document1Props, new ObjectId().toString(), false, WRITE_MODE.UPSERT, false);
        documentDAO.writeDocument(null, dbName, collection2Name, document2Id, null, null, document2Props, new ObjectId().toString(), false, WRITE_MODE.UPSERT, false);

        for (String doc : docsPropsStrings) {
            documentDAO.writeDocument(null, dbName, docsCollectionName, new ObjectId().toString(), null, null, BsonDocument.parse(doc), new ObjectId().toString(), false, WRITE_MODE.UPSERT, false);
        }
        LOG.debug("test data created");
    }
}
