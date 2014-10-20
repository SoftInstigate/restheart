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
package com.softinstigate.restheart.integrationtest;

import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import com.softinstigate.restheart.Configuration;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import java.net.URI;
import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.utils.URIBuilder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

/**
 *
 * @author uji
 */
public abstract class AbstactIT
{
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
    protected static String dbName = "integrationtestdb";
    protected static URI dbTmpUri;
    protected static String dbTmpName = "integrationtesttmpdb";
    protected static URI collection1Uri;
    protected static URI collection1UriRemappedAll;
    protected static URI collection1UriRemappedDb;
    protected static URI collection1UriRemappedCollection;
    protected static String collection1Name = "coll1";
    protected static URI collection2Uri;
    protected static URI collection2UriRemappedAll;
    protected static URI collection2UriRemappedDb;
    protected static URI collection2UriRemappedCollection;
    protected static String collection2Name = "coll2";
    protected static URI collectionTmpUri;
    protected static String collectionTmpName = "tmpcoll";
    protected static URI docsCollectionUri;
    protected static String docsCollectionName = "docscoll";
    protected static URI indexesUri;
    protected static URI document1Uri;
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
    protected static String document1Id = "doc1";
    protected static String document2Id = "doc2";
    protected static String documentTmpId = "tmpdoc";
    
    protected static String dbPropsString = "{ \"a\": 1, \"b\": \"due\", \"c\": { \"d\": 1, \"f\": [\"g\",\"h\",3,{\"i\":4, \"l\":\"tre\"}]}}";
    protected static String coll1PropsString = "{ \"a\":1, \"rels\" :  ["
            + "{ \"rel\": \"oto\", \"type\": \"ONE_TO_ONE\",  \"role\": \"OWNING\", \"target-coll\": \"coll2\", \"ref-field\": \"oto\" },"
            + "{ \"rel\": \"otm\", \"type\": \"ONE_TO_MANY\", \"role\": \"OWNING\", \"target-coll\": \"coll2\", \"ref-field\": \"otm\" },"
            + "{ \"rel\": \"mto\", \"type\": \"MANY_TO_ONE\", \"role\": \"OWNING\", \"target-coll\": \"coll2\", \"ref-field\": \"mto\" },"
            + "{ \"rel\": \"mtm\", \"type\": \"MANY_TO_MANY\", \"role\": \"OWNING\", \"target-coll\": \"coll2\", \"ref-field\": \"mtm\" }"
            + "]}";
    protected static String coll2PropsString = "{ \"a\":2, \"rels\" :  ["
            + "{ \"rel\": \"oto\", \"type\": \"ONE_TO_ONE\",  \"role\": \"INVERSE\", \"target-coll\": \"coll1\", \"ref-field\": \"oto\" },"
            + "{ \"rel\": \"mto\", \"type\": \"MANY_TO_ONE\", \"role\": \"INVERSE\", \"target-coll\": \"coll1\", \"ref-field\": \"otm\" },"
            + "{ \"rel\": \"otm\", \"type\": \"ONE_TO_MANY\", \"role\": \"INVERSE\", \"target-coll\": \"coll1\", \"ref-field\": \"mto\" },"
            + "{ \"rel\": \"mtm\", \"type\": \"MANY_TO_MANY\", \"role\": \"INVERSE\", \"target-coll\": \"coll1\", \"ref-field\": \"mtm\" }"
            + "]}";
    
    protected static String docsCollectionPropsStrings = "{}";
    
    protected static String collTmpPropsString =  "{ \"a\":1 }";
    
    protected static String document1PropsString = "{ \"a\": 1, \"oto\": \"doc2\", \"otm\" : [ \"doc2\" ], \"mto\" : \"doc2\", \"mtm\" : [ \"doc2\" ] }";
    protected static String document2PropsString = "{ \"a\": 2 }";
    
    protected static DBObject dbProps = (DBObject) JSON.parse(AbstactIT.dbPropsString);
    protected static DBObject coll1Props = (DBObject) JSON.parse(AbstactIT.coll1PropsString);
    protected static DBObject coll2Props = (DBObject) JSON.parse(AbstactIT.coll2PropsString);
    protected static DBObject collTmpProps = (DBObject) JSON.parse(AbstactIT.collTmpPropsString);
    protected static DBObject docsCollectionProps = (DBObject) JSON.parse(AbstactIT.docsCollectionPropsStrings);
    
    protected static DBObject document1Props = (DBObject) JSON.parse(AbstactIT.document1PropsString);
    protected static DBObject document2Props = (DBObject) JSON.parse(AbstactIT.document2PropsString);
    
    protected static String[] docsPropsStrings = {
        "{ \"ranking\": 1, \"name\": \"Nick\", \"surname\": \"Cave\", \"band\": \"Nick Cave & the Bad Seeds\"}",
        "{ \"ranking\": 2, \"name\": \"Robert\", \"surname\": \"Smith\", \"band\": \"The Cure\"}",
        "{ \"ranking\": 3, \"name\": \"Leonard\", \"surname\": \"Cohen\", \"band\": \"Leonard Cohen\"}",
        "{ \"ranking\": 4, \"name\": \"Tom\", \"surname\": \"Yorke\", \"band\": \"Radiohead\"}",
        "{ \"ranking\": 5, \"name\": \"Roger\", \"surname\": \"Waters\", \"band\": \"Pink Floyd\"}",
        "{ \"ranking\": 6, \"name\": \"Morrissey\", \"surname\": null, \"band\": \"The Smiths\"}",
        "{ \"ranking\": 7, \"name\": \"Mark\", \"surname\": \"Knopfler\", \"band\": \"Dire Straits\"}",
        "{ \"ranking\": 8, \"name\": \"Ramone\", \"surname\": \"Ramone\", \"band\": \"Ramones\"}",
        "{ \"ranking\": 9, \"name\": \"Ian\", \"surname\": \"Astbury\", \"band\": \"The Cult\"}",
        "{ \"ranking\": 10, \"name\": \"Polly Jean\", \"surname\": \"Harvey\", \"band\": \"PJ Harvey\"}",
    };
    // { keys: {a:1, b:-1} }
    protected static String[] docsCollectionIndexesStrings = {
        "{ \"name\": 1 }",
        "{ \"surname\": 1 }",
        "{ \"band\": 1 }",
        "{ \"ranking\": 1 }"
    };
    
    @Before
    public void setUp() throws Exception
    {
        conf = new Configuration(confFilePath);
        
        MongoDBClientSingleton.init(conf);
        
        mongoClient = MongoDBClientSingleton.getInstance().getClient();
        
        rootUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/")
                .build();
        
        rootUriRemapped = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappedall")
                .build();
        
        dbUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName)
                .build();
        
        dbUriRemappedAll = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappedall" + dbName)
                .build();
      
        dbUriRemappedDb = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappeddb" + dbName)
                .build();

        dbTmpUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/" + dbTmpName)
                .build();
        
        collection1Uri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + collection1Name)
                .build();
        
        collection1UriRemappedAll = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappedall/" + dbName + "/" + collection1Name)
                .build();
        
        collection1UriRemappedDb = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappeddb/" + collection1Name)
                .build();
        
        collection1UriRemappedCollection = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappedcoll1")
                .build();
        
        collection2Uri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + collection2Name)
                .build();
        
        collection2UriRemappedAll = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappedall/" + dbName + "/" + collection2Name)
                .build();
        
        collection2UriRemappedDb = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappeddb/" + collection2Name)
                .build();
        
        collection2UriRemappedCollection = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappedcoll2")
                .build();
        
        collectionTmpUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/" + dbTmpName + "/" + collectionTmpName)
                .build();
        
        docsCollectionUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + docsCollectionName)
                .build();
        
        indexesUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + docsCollectionName + "/_indexes")
                .build();
        
        documentTmpUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/" + dbTmpName + "/" + collectionTmpName + "/" + documentTmpId)
                .build();
        
        document1Uri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + collection1Name + "/" + document1Id)
                .build();
        
        document1UriRemappedAll = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappedall/" + dbName + "/" + collection1Name + "/" + document1Id)
                .build();
        
        document1UriRemappedDb = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappeddb/" + collection1Name + "/" + document1Id)
                .build();
        
        document1UriRemappedCollection = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappedcoll1" + "/" + document1Id)
                .build();
        
        document1UriRemappedDocument = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappeddoc1")
                .build();
        
        document2Uri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/" + dbName + "/" + collection2Name + "/" + document2Id)
                .build();
        
        document2UriRemappedAll = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappedall/" + dbName + "/" + collection1Name + "/" + document2Id)
                .build();
        
        document2UriRemappedDb = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappeddb/" + collection2Name + "/" + document2Id)
                .build();
        
        document2UriRemappedCollection = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappedcoll2" + "/" + document2Id)
                .build();
        
        document2UriRemappedDocument = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/remappeddoc2")
                .build();


        adminExecutor = Executor.newInstance().auth(new HttpHost(conf.getHttpHost()), "admin", "changeit");
        user1Executor = Executor.newInstance().auth(new HttpHost(conf.getHttpHost()), "user1", "changeit");
        user2Executor = Executor.newInstance().auth(new HttpHost(conf.getHttpHost()), "user2", "changeit");
        unauthExecutor= Executor.newInstance();
    }
    
    public AbstactIT()
    {
    }
    
    @BeforeClass
    public static void setUpClass()
    {
    }
    
    @AfterClass
    public static void tearDownClass()
    {
    }
    
    @After
    public void tearDown()
    {
    }
}