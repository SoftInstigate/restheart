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
import com.mongodb.util.JSON;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.db.DBDAO;
import com.softinstigate.restheart.db.DocumentDAO;
import com.softinstigate.restheart.db.IndexDAO;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import static com.softinstigate.restheart.integrationtest.AbstactIT.conf;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare
 */
public class TestDataInitializer extends AbstactIT {
    private static final Logger logger = LoggerFactory.getLogger(TestDataInitializer.class);

    public static void main(String[] args) throws Throwable {
        TestDataInitializer me = new TestDataInitializer();

        me.setUp();
        me.deleteExistingData();
        logger.info("existing data deleted");
        me.createTestData();
        logger.info("test data created");
    }

    private void deleteExistingData() {
        MongoDBClientSingleton.init(conf);

        MongoDBClientSingleton.getInstance().getClient().dropDatabase(dbName);
        MongoDBClientSingleton.getInstance().getClient().dropDatabase(dbTmpName);
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
    }
}
