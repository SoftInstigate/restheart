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
package org.restheart.test.integration;

import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import org.restheart.Configuration;
import org.restheart.db.DbsDAO;
import org.restheart.db.DocumentDAO;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.hal.Representation;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.restheart.db.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public abstract class AbstactIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstactIT.class);

    protected static final String CLIENT_HOST = "127.0.0.1";
    protected static final String HTTP = "http";

    protected static final Path confFilePath = new File("etc/restheart-integrationtest.yml").toPath();
    protected static MongoClient mongoClient;
    protected static Configuration conf = null;
    protected static Executor adminExecutor = null;
    protected static Executor user1Executor = null;
    protected static Executor user2Executor = null;
    protected static Executor unauthExecutor = null;

    protected static URI rootUri;
    protected static URI rootUriRemapped;
    protected static URI dbUri;
    protected static URI dbUriRemappedAll;
    protected static URI dbUriRemappedDb;
    protected static final String dbName = "mydb";
    protected static URI dbTmpUri;
    protected static final String dbTmpName = "mytmpdb";
    protected static URI collection1Uri;
    protected static URI collection1UriRemappedAll;
    protected static URI collection1UriRemappedDb;
    protected static URI collection1UriRemappedCollection;
    protected static final String collection1Name = "refcoll1";
    protected static URI collection2Uri;
    protected static URI collection2UriRemappedAll;
    protected static URI collection2UriRemappedDb;
    protected static URI collection2UriRemappedCollection;
    protected static final String collection2Name = "refcoll2";
    protected static URI collectionTmpUri;
    protected static final String collectionTmpName = "tmpcoll";
    protected static URI docsCollectionUri;
    protected static URI docsCollectionUriPaging;
    protected static URI docsCollectionUriCountAndPaging;
    protected static URI docsCollectionUriSort;
    protected static URI docsCollectionUriFilter;
    protected static final String docsCollectionName = "bandleaders";
    protected static URI indexesUri;
    protected static URI indexesUriRemappedAll;
    protected static URI indexesUriRemappedDb;
    protected static URI document1Uri;
    protected static URI dbUriPaging;
    protected static URI document1UriRemappedAll;
    protected static URI document1UriRemappedDb;
    protected static URI document1UriRemappedCollection;
    protected static URI document1UriRemappedDocument;
    protected static URI document2Uri;
    protected static URI document2UriRemappedAll;
    protected static URI document2UriRemappedDb;
    protected static URI document2UriRemappedCollection;
    protected static URI document2UriRemappedDocument;
    protected static URI documentTmpUri;
    protected static URI indexesTmpUri;
    protected static URI indexTmpUri;
    protected static final String document1Id = "doc1";
    protected static final String document2Id = "doc2";
    protected static final String documentTmpId = "tmpdoc";

    protected static final String dbPropsString = "{ \"a\": 1, \"b\": \"two\", \"c\": { \"d\": 3, \"f\": [\"g\",\"h\",4,{\"i\":5, \"l\":\"six\"}]}}";
    protected static final String coll1PropsString = "{ \"a\":1, \"rels\" :  ["
            + "{ \"rel\": \"oto\", \"type\": \"ONE_TO_ONE\",  \"role\": \"OWNING\", \"target-coll\": \"refcoll2\", \"ref-field\": \"oto\" },"
            + "{ \"rel\": \"otm\", \"type\": \"ONE_TO_MANY\", \"role\": \"OWNING\", \"target-coll\": \"refcoll2\", \"ref-field\": \"otm\" },"
            + "{ \"rel\": \"mto\", \"type\": \"MANY_TO_ONE\", \"role\": \"OWNING\", \"target-coll\": \"refcoll2\", \"ref-field\": \"mto\" },"
            + "{ \"rel\": \"mtm\", \"type\": \"MANY_TO_MANY\", \"role\": \"OWNING\", \"target-coll\": \"refcoll2\", \"ref-field\": \"mtm\" }"
            + "]}";
    protected static final String coll2PropsString = "{ \"a\":2, \"rels\" :  ["
            + "{ \"rel\": \"oto\", \"type\": \"ONE_TO_ONE\",  \"role\": \"INVERSE\", \"target-coll\": \"refcoll1\", \"ref-field\": \"oto\" },"
            + "{ \"rel\": \"mto\", \"type\": \"MANY_TO_ONE\", \"role\": \"INVERSE\", \"target-coll\": \"refcoll1\", \"ref-field\": \"otm\" },"
            + "{ \"rel\": \"otm\", \"type\": \"ONE_TO_MANY\", \"role\": \"INVERSE\", \"target-coll\": \"refcoll1\", \"ref-field\": \"mto\" },"
            + "{ \"rel\": \"mtm\", \"type\": \"MANY_TO_MANY\", \"role\": \"INVERSE\", \"target-coll\": \"refcoll1\", \"ref-field\": \"mtm\" }"
            + "]}";

    protected static final ContentType halCT = ContentType.create(Representation.HAL_JSON_MEDIA_TYPE);

    protected static String docsCollectionPropsStrings = "{}";

    protected static final String collTmpPropsString = "{ \"a\":1 }";

    protected static final String document1PropsString = "{ \"a\": 1, \"oto\": \"doc2\", \"otm\" : [ \"doc2\" ], \"mto\" : \"doc2\", \"mtm\" : [ \"doc2\" ] }";
    protected static final String document2PropsString = "{ \"a\": 2 }";

    protected static DBObject dbProps = (DBObject) JSON.parse(AbstactIT.dbPropsString);
    protected static DBObject coll1Props = (DBObject) JSON.parse(AbstactIT.coll1PropsString);
    protected static DBObject coll2Props = (DBObject) JSON.parse(AbstactIT.coll2PropsString);
    protected static DBObject collTmpProps = (DBObject) JSON.parse(AbstactIT.collTmpPropsString);
    protected static DBObject docsCollectionProps = (DBObject) JSON.parse(AbstactIT.docsCollectionPropsStrings);

    protected static DBObject document1Props = (DBObject) JSON.parse(AbstactIT.document1PropsString);
    protected static DBObject document2Props = (DBObject) JSON.parse(AbstactIT.document2PropsString);

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
    protected static final String[] docsCollectionIndexesStrings = {
        "{ \"name\": 1 }",
        "{ \"surname\": 1 }",
        "{ \"band\": 1 }",
        "{ \"ranking\": 1 }"
    };

    private final Database dbsDAO = new DbsDAO();

    public AbstactIT() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        LOGGER.info("@@@ setUpClass");

        conf = new Configuration(confFilePath);
        MongoDBClientSingleton.init(conf);
        mongoClient = MongoDBClientSingleton.getInstance().getClient();

        createURIs();

        final String host = CLIENT_HOST;
        final int port = conf.getHttpPort();
        adminExecutor = Executor.newInstance().authPreemptive(new HttpHost(host, port, HTTP)).auth(new HttpHost(host), "admin", "changeit");
        user1Executor = Executor.newInstance().authPreemptive(new HttpHost(host, port, HTTP)).auth(new HttpHost(host), "user1", "changeit");
        user2Executor = Executor.newInstance().authPreemptive(new HttpHost(host, port, HTTP)).auth(new HttpHost(host), "user2", "changeit");
        unauthExecutor = Executor.newInstance();
    }

    @AfterClass
    public static void tearDownClass() {
        LOGGER.info("@@@ tearDownClass");
    }

    @Before
    public void setUp() {
        LOGGER.info("setUp");
        createTestData();
    }

    @After
    public void tearDown() {
        LOGGER.info("tearDown");
        deleteTestData();
    }

    protected HttpResponse check(String message, Response resp, int expectedCode) throws Exception {
        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);

        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals(message, expectedCode, statusLine.getStatusCode());

        return httpResp;
    }

    private void createTestData() {
        dbsDAO.upsertDB(dbName, dbProps, new ObjectId(), false);

        dbsDAO.upsertCollection(dbName, collection1Name, coll1Props, new ObjectId(), false, false);
        dbsDAO.upsertCollection(dbName, collection2Name, coll2Props, new ObjectId(), false, false);
        dbsDAO.upsertCollection(dbName, docsCollectionName, docsCollectionProps, new ObjectId(), false, false);

        for (String index : docsCollectionIndexesStrings) {
            dbsDAO.createIndex(dbName, docsCollectionName, ((DBObject) JSON.parse(index)), null);
        }

        final DocumentDAO documentDAO = new DocumentDAO();
        documentDAO.upsertDocument(dbName, collection1Name, document1Id, document1Props, new ObjectId(), false);
        documentDAO.upsertDocument(dbName, collection2Name, document2Id, document2Props, new ObjectId(), false);

        for (String doc : docsPropsStrings) {
            documentDAO.upsertDocument(dbName, docsCollectionName, new ObjectId().toString(), ((DBObject) JSON.parse(doc)), new ObjectId(), false);
        }
        LOGGER.info("test data created");
    }

    private void deleteTestData() {
        List<String> databases = MongoDBClientSingleton.getInstance().getClient().getDatabaseNames();
        if (databases.contains(dbName)) {
            MongoDBClientSingleton.getInstance().getClient().dropDatabase(dbName);
        }
        if (databases.contains(dbTmpName)) {
            MongoDBClientSingleton.getInstance().getClient().dropDatabase(dbTmpName);
        }
        LOGGER.info("test data deleted");
    }

    private static void createURIs() throws URISyntaxException {
        rootUri = buildURI("/");
        rootUriRemapped = buildURI(REMAPPEDALL);

        dbUri = buildURI("/" + dbName);
        dbUriPaging = buildURI("/" + dbName,
                new NameValuePair[]{
                    new BasicNameValuePair("pagesize", "1")
                });

        dbUriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName);
        dbUriRemappedDb = buildURI(REMAPPEDDB);
        dbTmpUri = buildURI("/" + dbTmpName);

        collection1Uri = buildURI("/" + dbName + "/" + collection1Name);
        collection1UriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName + "/" + collection1Name);
        collection1UriRemappedDb = buildURI(REMAPPEDDB + "/" + collection1Name);
        collection1UriRemappedCollection = buildURI(REMAPPEDREFCOLL1);
        collection2Uri = buildURI("/" + dbName + "/" + collection2Name);
        collection2UriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName + "/" + collection2Name);
        collection2UriRemappedDb = buildURI(REMAPPEDDB + "/" + collection2Name);
        collection2UriRemappedCollection = buildURI(REMAPPEDREFCOLL2);
        collectionTmpUri = buildURI("/" + dbTmpName + "/" + collectionTmpName);

        docsCollectionUri = buildURI("/" + dbName + "/" + docsCollectionName);
        docsCollectionUriPaging = buildURI("/" + dbName + "/" + docsCollectionName,
                new NameValuePair[]{
                    new BasicNameValuePair("pagesize", "2")
                });
        
        docsCollectionUriCountAndPaging = buildURI("/" + dbName + "/" + docsCollectionName,
                new NameValuePair[]{
                    new BasicNameValuePair("count", null),
                    new BasicNameValuePair("page", "2"),
                    new BasicNameValuePair("pagesize", "2")
                });
        
        docsCollectionUriSort = buildURI("/" + dbName + "/" + docsCollectionName,
                new NameValuePair[]{
                    new BasicNameValuePair("sort_by", "surname")
                });
        
        docsCollectionUriFilter = buildURI("/" + dbName + "/" + docsCollectionName,
                new NameValuePair[]{
                    new BasicNameValuePair("filter", "{'name':{'$regex':'.*k$'}}"),
                    new BasicNameValuePair("sort_by", "name"),
                    new BasicNameValuePair("count", null)
                });

        indexesUri = buildURI("/" + dbName + "/" + docsCollectionName + _INDEXES);
        indexesUriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName + "/" + docsCollectionName + _INDEXES);
        indexesUriRemappedDb = buildURI(REMAPPEDDB + "/" + docsCollectionName + _INDEXES);
        indexesTmpUri = buildURI("/" + dbTmpName + "/" + collectionTmpName + _INDEXES);
        indexTmpUri = buildURI("/" + dbTmpName + "/" + collectionTmpName + _INDEXES + "/new-index");

        documentTmpUri = buildURI("/" + dbTmpName + "/" + collectionTmpName + "/" + documentTmpId);
        document1Uri = buildURI("/" + dbName + "/" + collection1Name + "/" + document1Id);
        document1UriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName + "/" + collection1Name + "/" + document1Id);
        document1UriRemappedDb = buildURI(REMAPPEDDB + "/" + collection1Name + "/" + document1Id);
        document1UriRemappedCollection = buildURI(REMAPPEDREFCOLL1 + "/" + document1Id);
        document1UriRemappedDocument = buildURI(REMAPPEDDOC1);
        document2Uri = buildURI("/" + dbName + "/" + collection2Name + "/" + document2Id);
        document2UriRemappedAll = buildURI(REMAPPEDALL + "/" + dbName + "/" + collection1Name + "/" + document2Id);
        document2UriRemappedDb = buildURI(REMAPPEDDB + "/" + collection2Name + "/" + document2Id);
        document2UriRemappedCollection = buildURI(REMAPPEDREFCOLL2 + "/" + document2Id);
        document2UriRemappedDocument = buildURI(REMAPPEDDOC2);
    }

    protected static URI buildURI(String path, NameValuePair[] parameters) throws URISyntaxException {
        return createURIBuilder(path)
                .addParameters(Arrays.asList(parameters))
                .build();
    }

    protected static URI buildURI(String path) throws URISyntaxException {
        return createURIBuilder(path)
                .build();
    }

    private static URIBuilder createURIBuilder(String path) {
        return new URIBuilder()
                .setScheme(HTTP)
                .setHost(CLIENT_HOST)
                .setPort(conf.getHttpPort())
                .setPath(path);
    }

    private static final String _INDEXES = "/_indexes";
    private static final String REMAPPEDDOC1 = "/remappeddoc1";
    private static final String REMAPPEDALL = "/remappedall";
    private static final String REMAPPEDDB = "/remappeddb";
    private static final String REMAPPEDREFCOLL2 = "/remappedrefcoll2";
    private static final String REMAPPEDDOC2 = "/remappeddoc2";
    private static final String REMAPPEDREFCOLL1 = "/remappedrefcoll1";
}
