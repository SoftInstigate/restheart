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
package com.softinstigate.restheart.integrationtest;

import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import com.softinstigate.restheart.Configuration;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.db.DBDAO;
import com.softinstigate.restheart.db.DocumentDAO;
import com.softinstigate.restheart.db.IndexDAO;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.hal.Representation;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import static org.junit.Assert.*;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare
 */
public abstract class AbstactIT {

    private static final Logger LOG = LoggerFactory.getLogger(AbstactIT.class);

    private static final String HOST = "127.0.0.1";
    private static final String HTTP = "http";

    protected static final String confFilePath = "etc/restheart-integrationtest.yml";
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

    public AbstactIT() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        LOG.info("@@@ Initializing integration tests");

        conf = new Configuration(confFilePath);
        MongoDBClientSingleton.init(conf);
        mongoClient = MongoDBClientSingleton.getInstance().getClient();

        createURIs();

        adminExecutor = Executor.newInstance().authPreemptive(new HttpHost(HOST, 8080, HTTP)).auth(new HttpHost(HOST), "admin", "changeit");
        user1Executor = Executor.newInstance().authPreemptive(new HttpHost(HOST, 8080, HTTP)).auth(new HttpHost(HOST), "user1", "changeit");
        user2Executor = Executor.newInstance().authPreemptive(new HttpHost(HOST, 8080, HTTP)).auth(new HttpHost(HOST), "user2", "changeit");
        unauthExecutor = Executor.newInstance();
    }

    @AfterClass
    public static void tearDownClass() {
        LOG.info("@@@ Cleaning-up integration tests");
    }

    @Before
    public void setUp() {
        createTestData();
    }

    @After
    public void tearDown() {
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
        DBDAO.upsertDB(dbName, dbProps, new ObjectId(), false);
        CollectionDAO.upsertCollection(dbName, collection1Name, coll1Props, new ObjectId(), false, false);
        CollectionDAO.upsertCollection(dbName, collection2Name, coll2Props, new ObjectId(), false, false);
        CollectionDAO.upsertCollection(dbName, docsCollectionName, docsCollectionProps, new ObjectId(), false, false);

        for (String index : docsCollectionIndexesStrings) {
            IndexDAO.createIndex(dbName, docsCollectionName, ((DBObject) JSON.parse(index)), null);
        }

        DocumentDAO.upsertDocument(dbName, collection1Name, document1Id, document1Props, new ObjectId(), false);
        DocumentDAO.upsertDocument(dbName, collection2Name, document2Id, document2Props, new ObjectId(), false);

        for (String doc : docsPropsStrings) {
            DocumentDAO.upsertDocument(dbName, docsCollectionName, new ObjectId().toString(), ((DBObject) JSON.parse(doc)), new ObjectId(), false);
        }
        LOG.info("test data created");
    }

    private void deleteTestData() {
        List<String> databases = MongoDBClientSingleton.getInstance().getClient().getDatabaseNames();
        if (databases.contains(dbName)) {
            MongoDBClientSingleton.getInstance().getClient().dropDatabase(dbName);
        }
        if (databases.contains(dbTmpName)) {
            MongoDBClientSingleton.getInstance().getClient().dropDatabase(dbTmpName);
        }
        LOG.info("existing data deleted");
    }

    private static void createURIs() throws URISyntaxException {
        rootUri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/")
                .build();

        rootUriRemapped = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDALL)
                .build();

        dbUri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName)
                .build();

        dbUriPaging = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName)
                .addParameter("pagesize", "1")
                .build();

        dbUriRemappedAll = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDALL + "/" + dbName)
                .build();

        dbUriRemappedDb = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDDB)
                .build();

        dbTmpUri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbTmpName)
                .build();

        collection1Uri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + collection1Name)
                .build();

        collection1UriRemappedAll = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDALL + "/" + dbName + "/" + collection1Name)
                .build();

        collection1UriRemappedDb = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDDB + "/" + collection1Name)
                .build();

        collection1UriRemappedCollection = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDREFCOLL1)
                .build();

        collection2Uri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + collection2Name)
                .build();

        collection2UriRemappedAll = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDALL + "/" + dbName + "/" + collection2Name)
                .build();

        collection2UriRemappedDb = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDDB + "/" + collection2Name)
                .build();

        collection2UriRemappedCollection = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDREFCOLL2)
                .build();

        collectionTmpUri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbTmpName + "/" + collectionTmpName)
                .build();

        docsCollectionUri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + docsCollectionName)
                .build();

        docsCollectionUriPaging = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + docsCollectionName)
                .addParameter("pagesize", "2")
                .build();

        docsCollectionUriCountAndPaging = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + docsCollectionName)
                .addParameter("count", null)
                .addParameter("page", "2")
                .addParameter("pagesize", "2")
                .build();

        docsCollectionUriSort = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + docsCollectionName)
                .addParameter("sort_by", "surname")
                .build();

        docsCollectionUriFilter = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + docsCollectionName)
                .addParameter("filter", "{'name':{'$regex' : '.*k$'}}")
                .addParameter("sort_by", "name")
                .addParameter("count", null)
                .build();

        indexesUri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + docsCollectionName + _INDEXES)
                .build();

        indexesUriRemappedAll = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDALL + "/" + dbName + "/" + docsCollectionName + _INDEXES)
                .build();

        indexesUriRemappedDb = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDDB + "/" + docsCollectionName + _INDEXES)
                .build();

        documentTmpUri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbTmpName + "/" + collectionTmpName + "/" + documentTmpId)
                .build();

        indexesTmpUri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbTmpName + "/" + collectionTmpName + _INDEXES)
                .build();
        indexTmpUri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbTmpName + "/" + collectionTmpName + _INDEXES + "/new-index")
                .build();

        document1Uri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + collection1Name + "/" + document1Id)
                .build();

        document1UriRemappedAll = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDALL + "/" + dbName + "/" + collection1Name + "/" + document1Id)
                .build();

        document1UriRemappedDb = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDDB + "/" + collection1Name + "/" + document1Id)
                .build();

        document1UriRemappedCollection = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDREFCOLL1 + "/" + document1Id)
                .build();

        document1UriRemappedDocument = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDDOC1)
                .build();

        document2Uri = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + collection2Name + "/" + document2Id)
                .build();

        document2UriRemappedAll = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDALL + "/" + dbName + "/" + collection1Name + "/" + document2Id)
                .build();

        document2UriRemappedDb = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDDB + "/" + collection2Name + "/" + document2Id)
                .build();

        document2UriRemappedCollection = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDREFCOLL2 + "/" + document2Id)
                .build();

        document2UriRemappedDocument = new URIBuilder()
                .setScheme(HTTP)
                .setHost(HOST)
                .setPort(conf.getHttpPort())
                .setPath(REMAPPEDDOC2)
                .build();
    }

    private static final String _INDEXES = "/_indexes";
    private static final String REMAPPEDDOC1 = "/remappeddoc1";
    private static final String REMAPPEDALL = "/remappedall";
    private static final String REMAPPEDDB = "/remappeddb";
    private static final String REMAPPEDREFCOLL2 = "/remappedrefcoll2";
    private static final String REMAPPEDDOC2 = "/remappeddoc2";
    private static final String REMAPPEDREFCOLL1 = "/remappedrefcoll1";
}
