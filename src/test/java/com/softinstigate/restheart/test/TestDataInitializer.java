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
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.db.DBDAO;
import com.softinstigate.restheart.db.DocumentDAO;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import static com.softinstigate.restheart.test.AbstactIT.conf;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class TestDataInitializer extends AbstactIT
{
    private static final Logger logger = LoggerFactory.getLogger(TestDataInitializer.class);
    
    public static void main(String[] args) throws Throwable
    {
        TestDataInitializer me = new TestDataInitializer();
        
        me.setUp();
        me.deleteExistingData();
        logger.info("existing data deleted");
        me.createTestData();
        logger.info("test data created");
    }
    
    private void deleteExistingData()
    {
        MongoDBClientSingleton.init(conf);
        
        if (DBDAO.doesDbExists(dbName))
        {
            DBObject md = DBDAO.getDbMetaData(dbName);
            
            Object o = md.get("@etag");
            
            if (o == null || !(o instanceof ObjectId))
            {
                throw new RuntimeException("error. cannot delete existing test data. db " + dbName + " does not have a valid etag property");
            }
            
            ObjectId etag = (ObjectId) o ;
            
            DBDAO.deleteDB(dbName, etag);
        }
        
        if (DBDAO.doesDbExists(dbTmpName))
        {
            DBObject md = DBDAO.getDbMetaData(dbTmpName);
            
            Object o = md.get("@etag");
            
            if (o == null || !(o instanceof String) || !ObjectId.isValid((String)o))
            {
                throw new RuntimeException("error. cannot delete existing test data. db " + dbTmpName + " does not have a valid etag property");
            }
            
            ObjectId etag = new ObjectId((String)o);
            
            DBDAO.deleteDB(dbTmpName, etag);
        }
    }
    
    private void createTestData()
    {
        DBDAO.upsertDB(dbName, dbProps, new ObjectId(), false);
        CollectionDAO.upsertCollection(dbName, collection1Name, coll1Props, new ObjectId(), false, false);
        CollectionDAO.upsertCollection(dbName, collection2Name, coll2Props, new ObjectId(), false, false);
        DocumentDAO.upsertDocument(dbName, collection1Name, document1Id, document1Props, new ObjectId(), false);
        DocumentDAO.upsertDocument(dbName, collection2Name, document2Id, document2Props, new ObjectId(), false);
    }
}
