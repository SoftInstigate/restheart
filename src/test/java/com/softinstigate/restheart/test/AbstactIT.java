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
package com.softinstigate.restheart.test;

import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import com.softinstigate.restheart.Configuration;
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
    protected static final String confFilePath = "etc/restheart.yml";
    protected static Configuration conf = null;
    protected static Executor adminExecutor = null;
    protected static Executor userExecutor = null;
    protected static Executor unauthExecutor = null;
    
    protected static URI rootUri;
    protected static URI dbUri;
    protected static String dbName = "integrationtestdb";
    protected static URI dbTmpUri;
    protected static String dbTmpName = "integrationtesttmpdb";
    protected static URI collection1Uri ;
    protected static String collection1Name = "coll1";
    protected static URI collection2Uri;
    protected static String collection2Name = "coll2";
    protected static URI collectionTmpUri;
    protected static String collectionTmpName = "tmpcoll";
    protected static URI indexesUri;
    protected static URI document1Uri;
    protected static URI document2Uri;
    protected static String document1Id = "doc1";
    protected static String document2Id = "doc2";
    
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
    protected static String collTmpPropsString =  "{ \"a\":1 }";
    
    protected static String document1PropsString = "{ \"a\": 1, \"oto\": \"doc2\", \"otm\" : [ \"doc2\" ], \"mto\" : \"doc2\", \"mtm\" : [ \"doc2\" ] }";
    protected static String document2PropsString = "{ \"a\": 2 }";
    
    protected static DBObject dbProps = (DBObject) JSON.parse(AbstactIT.dbPropsString);
    protected static DBObject coll1Props = (DBObject) JSON.parse(AbstactIT.coll1PropsString);
    protected static DBObject coll2Props = (DBObject) JSON.parse(AbstactIT.coll2PropsString);
    protected static DBObject collTmpProps = (DBObject) JSON.parse(AbstactIT.collTmpPropsString);
    
    protected static DBObject document1Props = (DBObject) JSON.parse(AbstactIT.document1PropsString);
    protected static DBObject document2Props = (DBObject) JSON.parse(AbstactIT.document2PropsString);
    
    @Before
    public void setUp() throws Exception
    {
        conf = new Configuration(confFilePath);
        
        rootUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/")
                .build();
        
        dbUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/integrationtestdb")
                .build();

        dbUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/integrationtesttmpdb")
                .build();
        
        collection1Uri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/integrationtestdb/coll1")
                .build();
        
        collection2Uri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/integrationtestdb/coll2")
                .build();
        
        collectionTmpUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/integrationtesttmpdb/tmpcoll")
                .build();
        
        indexesUri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/integrationtestdb/coll/@indexes")
                .build();
        
        document1Uri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/integrationtestdb/coll1/doc1")
                .build();
        
        document2Uri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/integrationtestdb/coll2/doc2")
                .build();


        adminExecutor = Executor.newInstance().auth(new HttpHost(conf.getHttpHost()), "a", "a");
        userExecutor = Executor.newInstance().auth(new HttpHost(conf.getHttpHost()), "user", "changeit");
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